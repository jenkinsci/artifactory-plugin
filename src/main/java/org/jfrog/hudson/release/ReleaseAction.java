/*
 * Copyright (C) 2011 JFrog Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jfrog.hudson.release;

import com.google.common.collect.Maps;
import hudson.maven.MavenModule;
import hudson.maven.MavenModuleSet;
import hudson.maven.ModuleName;
import hudson.model.Action;
import hudson.model.Cause;
import org.apache.commons.lang.StringUtils;
import org.jfrog.hudson.ArtifactoryRedeployPublisher;
import org.jfrog.hudson.action.ActionableHelper;
import org.jfrog.hudson.release.scm.svn.SubversionManager;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

/**
 * This action leads to execution of the release wrapper. It will collect information from the user about the release
 * and will trigger the release build.
 * This action is not saved in the job xml file.
 *
 * @author Yossi Shaul
 */
public class ReleaseAction implements Action {

    private transient MavenModuleSet project;

    VERSIONING versioning;

    /**
     * The next release version to change the model to if using one global version.
     */
    String releaseVersion;
    /**
     * Next (development) version to change the model to if using one global version.
     */
    String nextVersion;
    /**
     * Map of release versions per module. Only used if versioning is per module
     */
    Map<ModuleName, String> releaseVersionPerModule;
    /**
     * Map of dev versions per module. Only used if versioning is per module
     */
    Map<ModuleName, String> nextVersionPerModule;

    boolean createVcsTag;
    String tagUrl;
    String tagComment;
    String stagingRepositoryKey;
    String stagingComment;
    // TODO: maybe it should be saved in the scm coordinator
    boolean tagCreated;

    public enum VERSIONING {
        GLOBAL, PER_MODULE, NONE
    }

    public ReleaseAction(MavenModuleSet project) {
        this.project = project;
    }

    public String getIconFileName() {
        if (project.hasPermission(ReleaseWrapper.RELEASE)) {
            return "/plugin/artifactory/images/artifactory-release.png";
        }

        // return null to hide the action (doSubmit will also perform permission check if someone tries direct link)
        return null;
    }

    /**
     * @return The message to display on the left panel for the perform release action.
     */
    public String getDisplayName() {
        return "Artifactory Release Staging";
    }

    public String getUrlName() {
        return "release";
    }

    public boolean isCreateVcsTag() {
        return createVcsTag;
    }

    public boolean isTagCreated() {
        return tagCreated;
    }

    public void setTagCreated(boolean tagCreated) {
        this.tagCreated = tagCreated;
    }

    public String getTagUrl() {
        return tagUrl;
    }

    public String getTagComment() {
        return tagComment;
    }

    public Collection<MavenModule> getModules() {
        return project.getDisabledModules(false);
    }

    public String getStagingComment() {
        return stagingComment;
    }

    public String getStagingRepositoryKey() {
        return stagingRepositoryKey;
    }

    public String latestVersioningSelection() {
        return VERSIONING.GLOBAL.name();
    }

    public String getDefaultTagUrl() {
        ReleaseWrapper wrapper = ActionableHelper.getReleaseWrapper(project, ReleaseWrapper.class);
        if (wrapper == null) {
            return null;
        }
        String baseTagUrl = wrapper.getBaseTagUrl();
        StringBuilder sb = new StringBuilder(baseTagUrl);
        if (!baseTagUrl.endsWith("/")) {
            sb.append("/");
        }
        sb.append(getRootModule().getModuleName().artifactId).append("-").append(calculateReleaseVersion());
        return sb.toString();
    }

    public String getDefaultTagComment() {
        return SubversionManager.COMMENT_PREFIX + "Creating release tag for version " + calculateReleaseVersion();
    }

    /**
     * @return The release repository configured in Artifactory publisher.
     */
    public String getDefaultStagingRepository() {
        ArtifactoryRedeployPublisher publisher = ActionableHelper.getPublisher(
                project, ArtifactoryRedeployPublisher.class);
        if (publisher == null) {
            return null;
        }
        return publisher.getRepositoryKey();
    }

    public String getCurrentVersion() {
        return getRootModule().getVersion();
    }

    private MavenModule getRootModule() {
        return project.getRootModule();
    }

    public String calculateReleaseVersion() {
        return calculateReleaseVersion(getCurrentVersion());
    }

    public String calculateReleaseVersion(String fromVersion) {
        return fromVersion.replace("-SNAPSHOT", "");
    }

    /**
     * Calculates the next snapshot version based on the current release version
     *
     * @param currentVersion A release version
     * @return The next calculated development (snapshot) version
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public String calculateNextVersion() {
        return calculateNextVersion(calculateReleaseVersion());
    }

    /**
     * Calculates the next snapshot version based on the current release version
     *
     * @param fromVersion The version to bump to next development version
     * @return The next calculated development (snapshot) version
     */
    public String calculateNextVersion(String fromVersion) {
        // first turn it to release version
        fromVersion = calculateReleaseVersion(fromVersion);
        String nextVersion;
        int lastDotIndex = fromVersion.lastIndexOf('.');
        try {
            if (lastDotIndex != -1) {
                // probably a major minor version e.g., 2.1.1
                String minorVersionToken = fromVersion.substring(lastDotIndex + 1);
                String nextMinorVersion;
                int lastDashIndex = minorVersionToken.lastIndexOf('-');
                if (lastDashIndex != -1) {
                    // probably a minor-buildNum e.g., 2.1.1-4 (should change to 2.1.1-5)
                    String buildNumber = minorVersionToken.substring(lastDashIndex + 1);
                    int nextBuildNumber = Integer.parseInt(buildNumber) + 1;
                    nextMinorVersion = minorVersionToken.substring(0, lastDashIndex + 1) + nextBuildNumber;
                } else {
                    nextMinorVersion = Integer.parseInt(minorVersionToken) + 1 + "";
                }
                nextVersion = fromVersion.substring(0, lastDotIndex + 1) + nextMinorVersion;
            } else {
                // maybe it's just a major version; try to parse as an int
                int nextMajorVersion = Integer.parseInt(fromVersion) + 1;
                nextVersion = nextMajorVersion + "";
            }
        } catch (NumberFormatException e) {
            nextVersion = "Next.Version";
        }
        return nextVersion + "-SNAPSHOT";
    }

    /**
     * @return List of target repositories for deployment (release repositories first). Called from the UI.
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public List<String> getRepositoryKeys() {
        ArtifactoryRedeployPublisher artifactoryPublisher =
                ActionableHelper.getPublisher(project, ArtifactoryRedeployPublisher.class);
        if (artifactoryPublisher != null) {
            return artifactoryPublisher.getArtifactoryServer().getReleaseRepositoryKeysFirst();
        } else {
            return Collections.emptyList();
        }
    }

    public String lastStagingRepository() {
        // prefer the release repository defined in artifactory publisher
        ArtifactoryRedeployPublisher artifactoryPublisher =
                ActionableHelper.getPublisher(project, ArtifactoryRedeployPublisher.class);
        return artifactoryPublisher != null ? artifactoryPublisher.getRepositoryKey() : null;
    }

    /**
     * Form submission is calling this method
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public void doSubmit(StaplerRequest req, StaplerResponse resp) throws IOException, ServletException {
        // enforce release permissions
        project.checkPermission(ReleaseWrapper.RELEASE);

        req.bindParameters(this);

        String versioningStr = req.getParameter("versioning");
        versioning = VERSIONING.valueOf(versioningStr);
        switch (versioning) {
            case GLOBAL:
                releaseVersion = req.getParameter("releaseVersion");
                nextVersion = req.getParameter("nextVersion");
                break;
            case PER_MODULE:
                releaseVersionPerModule = Maps.newHashMap();
                nextVersionPerModule = Maps.newHashMap();
                Enumeration params = req.getParameterNames();
                while (params.hasMoreElements()) {
                    String key = (String) params.nextElement();
                    if (key.startsWith("release.")) {
                        ModuleName moduleName = ModuleName.fromString(StringUtils.removeStart(key, "release."));
                        releaseVersionPerModule.put(moduleName, req.getParameter(key));
                    } else if (key.startsWith("next.")) {
                        ModuleName moduleName = ModuleName.fromString(StringUtils.removeStart(key, "next."));
                        nextVersionPerModule.put(moduleName, req.getParameter(key));
                    }
                }
        }
        createVcsTag = req.getParameter("createVcsTag") != null;
        if (createVcsTag) {
            tagUrl = req.getParameter("tagUrl");
            tagComment = req.getParameter("tagComment");
        }

        stagingRepositoryKey = req.getParameter("repositoryKey");
        stagingComment = req.getParameter("stagingComment");

        // schedule release build
        if (project.scheduleBuild(0, new Cause.UserCause(), this)) {
            // redirect to the project page
            resp.sendRedirect(project.getAbsoluteUrl());
        }
    }

    public String getReleaseVersionFor(ModuleName moduleName) {
        switch (versioning) {
            case GLOBAL:
                return releaseVersion;
            case PER_MODULE:
                return releaseVersionPerModule.get(moduleName);
            default:
                return null;
        }
    }

    public String getNextVersionFor(ModuleName moduleName) {
        switch (versioning) {
            case GLOBAL:
                return nextVersion;
            case PER_MODULE:
                return nextVersionPerModule.get(moduleName);
            default:
                return null;
        }
    }
}
