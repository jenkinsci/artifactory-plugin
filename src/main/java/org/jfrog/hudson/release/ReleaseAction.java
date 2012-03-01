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
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildableItemWithBuildWrappers;
import hudson.model.Cause;
import hudson.tasks.BuildWrapper;
import org.jfrog.hudson.ArtifactoryPlugin;
import org.jfrog.hudson.ArtifactoryServer;
import org.jfrog.hudson.PluginSettings;
import org.jfrog.hudson.UserPluginInfo;
import org.jfrog.hudson.action.ActionableHelper;
import org.jfrog.hudson.release.scm.AbstractScmCoordinator;
import org.jfrog.hudson.release.scm.svn.SubversionManager;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * This action leads to execution of the release wrapper. It will collect information from the user about the release
 * and will trigger the release build. This action is not saved in the job xml file.
 *
 * @author Yossi Shaul
 */
public abstract class ReleaseAction<P extends AbstractProject & BuildableItemWithBuildWrappers,
        W extends BuildWrapper> implements Action {

    protected final transient P project;
    private Class<W> wrapperClass;

    protected VERSIONING versioning;

    /**
     * The next release version to change the model to if using one global version.
     */
    protected String releaseVersion;
    /**
     * Next (development) version to change the model to if using one global version.
     */
    protected String nextVersion;

    Boolean pro;

    boolean createVcsTag;
    String tagUrl;
    String tagComment;
    String nextDevelCommitComment;
    String stagingRepositoryKey;
    String stagingComment;

    boolean createReleaseBranch;
    String releaseBranch;

    protected transient boolean strategyPluginExists;
    protected transient Map stagingStrategy;
    protected transient VersionedModule defaultGlobalModule;
    protected transient Map<String, VersionedModule> defaultModules;
    protected transient VcsConfig defaultVcsConfig;
    protected transient PromotionConfig defaultPromotionConfig;

    public enum VERSIONING {
        GLOBAL, PER_MODULE, NONE
    }

    public ReleaseAction(P project, Class<W> wrapperClass) {
        this.project = project;
        this.wrapperClass = wrapperClass;
    }

    public void init() throws IOException, InterruptedException {
        PluginSettings selectedStagingPluginSettings = getSelectedStagingPlugin();
        if ((selectedStagingPluginSettings != null) &&
                !UserPluginInfo.NO_PLUGIN_KEY.equals(selectedStagingPluginSettings.getPluginName())) {
            stagingStrategy = getArtifactoryServer().getStagingStrategy(selectedStagingPluginSettings,
                    project.getName());
            strategyPluginExists = (stagingStrategy != null) && !stagingStrategy.isEmpty();
        }

        prepareDefaultGlobalModule();
        prepareDefaultModules();
        prepareDefaultVcsSettings();
        prepareDefaultPromotionConfig();
    }

    public String getIconFileName() {
        if (project.hasPermission(ArtifactoryPlugin.RELEASE)) {
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

    public VERSIONING getVersioning() {
        return versioning;
    }

    public String getReleaseVersion() {
        return releaseVersion;
    }

    public String getNextVersion() {
        return nextVersion;
    }

    public boolean isCreateVcsTag() {
        return createVcsTag;
    }

    public String getTagUrl() {
        return tagUrl;
    }

    public String getTagComment() {
        return tagComment;
    }

    public String getNextDevelCommitComment() {
        return nextDevelCommitComment;
    }

    public boolean isCreateReleaseBranch() {
        return createReleaseBranch;
    }

    public String getReleaseBranch() {
        return releaseBranch;
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

    public boolean isArtifactoryPro() {
        return getArtifactoryServer().isArtifactoryPro();
    }

    public String getDefaultGlobalReleaseVersion() {
        return (defaultGlobalModule != null) ? defaultGlobalModule.getReleaseVersion() : null;
    }

    public String getDefaultGlobalNextDevelopmentVersion() {
        return (defaultGlobalModule != null) ? defaultGlobalModule.getNextDevelopmentVersion() : null;
    }

    public Collection<VersionedModule> getDefaultModules() {
        return defaultModules.values();
    }

    public boolean isGit() {
        return AbstractScmCoordinator.isGitScm(project);
    }

    public VcsConfig getDefaultVcsConfig() {
        return defaultVcsConfig;
    }

    public PromotionConfig getDefaultPromotionConfig() {
        return defaultPromotionConfig;
    }

    /**
     * @return List of target repositories for deployment (release repositories first). Called from the UI.
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public abstract List<String> getRepositoryKeys();

    public abstract ArtifactoryServer getArtifactoryServer();

    /**
     * Form submission is calling this method
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public void doSubmit(StaplerRequest req, StaplerResponse resp) throws IOException, ServletException {
        // enforce release permissions
        project.checkPermission(ArtifactoryPlugin.RELEASE);

        req.bindParameters(this);

        String versioningStr = req.getParameter("versioning");
        versioning = VERSIONING.valueOf(versioningStr);
        switch (versioning) {
            case GLOBAL:
                doGlobalVersioning(req);
                break;
            case PER_MODULE:
                doPerModuleVersioning(req);
        }
        createVcsTag = req.getParameter("createVcsTag") != null;
        if (createVcsTag) {
            tagUrl = req.getParameter("tagUrl");
            tagComment = req.getParameter("tagComment");
        }
        nextDevelCommitComment = req.getParameter("nextDevelCommitComment");
        createReleaseBranch = req.getParameter("createReleaseBranch") != null;
        if (createReleaseBranch) {
            releaseBranch = req.getParameter("releaseBranch");
        }

        stagingRepositoryKey = req.getParameter("repositoryKey");
        stagingComment = req.getParameter("stagingComment");

        // schedule release build
        if (project.scheduleBuild(0, new Cause.UserIdCause(), this)) {
            // redirect to the project page
            resp.sendRedirect(project.getAbsoluteUrl());
        }
    }

    public abstract String getReleaseVersionFor(Object moduleName);

    public abstract String getNextVersionFor(Object moduleName);

    protected String getDefaultNextDevelCommitMessage() {
        return SubversionManager.COMMENT_PREFIX + "Next development version";
    }

    protected String getBaseTagUrlAccordingToScm(String baseTagUrl) {
        if (AbstractScmCoordinator.isSvn(project) && !baseTagUrl.endsWith("/")) {
            return baseTagUrl + "/";
        }
        return baseTagUrl;
    }

    /**
     * Execute the {@link VERSIONING#GLOBAL} strategy of the versioning mechanism, which assigns all modules the same
     * version for the release and for the next development version.
     *
     * @param req The request that is coming from the form when staging.
     */
    protected void doGlobalVersioning(StaplerRequest req) {
        releaseVersion = req.getParameter("releaseVersion");
        nextVersion = req.getParameter("nextVersion");
    }

    protected W getWrapper() {
        return ActionableHelper.getBuildWrapper(project, wrapperClass);
    }

    protected String calculateReleaseVersion(String fromVersion) {
        return fromVersion.replace("-SNAPSHOT", "");
    }

    /**
     * Calculates the next snapshot version based on the current release version
     *
     * @param fromVersion The version to bump to next development version
     * @return The next calculated development (snapshot) version
     */
    protected String calculateNextVersion(String fromVersion) {
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
            return fromVersion;
        }
        return nextVersion + "-SNAPSHOT";
    }

    /**
     * Execute the {@link VERSIONING#PER_MODULE} strategy of the versioning mechanism, which assigns each module its own
     * version for release and for the next development version
     *
     * @param req The request that is coming from the form when staging.
     */
    protected abstract void doPerModuleVersioning(StaplerRequest req);

    protected abstract PluginSettings getSelectedStagingPlugin();

    protected abstract void prepareBuilderSpecificDefaultGlobalModule();

    protected abstract void prepareBuilderSpecificDefaultModules();

    protected abstract void prepareBuilderSpecificDefaultVcsConfig();

    protected abstract void prepareBuilderSpecificDefaultPromotionConfig();

    private void prepareDefaultGlobalModule() {
        if (strategyPluginExists) {
            if (stagingStrategy.containsKey("defaultModuleVersion")) {
                Map<String, String> defaultModuleVersion =
                        (Map<String, String>) stagingStrategy.get("defaultModuleVersion");
                defaultGlobalModule = new VersionedModule(defaultModuleVersion.get("moduleId"),
                        defaultModuleVersion.get("nextRelease"), defaultModuleVersion.get("nextDevelopment"));
            }
        }

        if (defaultGlobalModule == null) {
            prepareBuilderSpecificDefaultGlobalModule();
        }
    }

    private void prepareDefaultModules() {
        if (strategyPluginExists) {
            if (stagingStrategy.containsKey("moduleVersionsMap")) {
                Map<String, ? extends Map<String, String>> moduleVersionsMap =
                        (Map<String, ? extends Map<String, String>>) stagingStrategy.get("moduleVersionsMap");
                defaultModules = Maps.newHashMap();
                if (!moduleVersionsMap.isEmpty()) {
                    for (Map<String, String> moduleVersion : moduleVersionsMap.values()) {
                        String moduleId = moduleVersion.get("moduleId");
                        defaultModules.put(moduleId, new VersionedModule(moduleId, moduleVersion.get("nextRelease"),
                                moduleVersion.get("nextDevelopment")));
                    }
                }
            }
        }

        if (defaultModules == null) {
            prepareBuilderSpecificDefaultModules();
        }
    }

    private void prepareDefaultVcsSettings() {
        if (strategyPluginExists) {
            if (stagingStrategy.containsKey("vcsConfig")) {
                Map<String, Object> vcsConfig = (Map<String, Object>) stagingStrategy.get("vcsConfig");
                defaultVcsConfig = new VcsConfig(((Boolean) vcsConfig.get("useReleaseBranch")),
                        getStagingConfigAsString(vcsConfig, "releaseBranchName"),
                        ((Boolean) vcsConfig.get("createTag")),
                        getStagingConfigAsString(vcsConfig, "tagUrlOrName"),
                        getStagingConfigAsString(vcsConfig, "tagComment"),
                        getStagingConfigAsString(vcsConfig, "nextDevelopmentVersionComment"));
            }
        }

        if (defaultVcsConfig == null) {
            prepareBuilderSpecificDefaultVcsConfig();
        }
    }

    private void prepareDefaultPromotionConfig() {
        if (strategyPluginExists) {
            if (stagingStrategy.containsKey("promotionConfig")) {
                Map<String, String> promotionConfig = (Map<String, String>) stagingStrategy.get("promotionConfig");
                defaultPromotionConfig = new PromotionConfig(promotionConfig.get("targetRepository"),
                        promotionConfig.get("comment"));
            }
        }

        if (defaultPromotionConfig == null) {
            prepareBuilderSpecificDefaultPromotionConfig();
        }
    }

    private String getStagingConfigAsString(Map<String, Object> configMap, String key) {
        if (configMap.containsKey(key)) {
            return configMap.get(key).toString();
        }
        return null;
    }
}
