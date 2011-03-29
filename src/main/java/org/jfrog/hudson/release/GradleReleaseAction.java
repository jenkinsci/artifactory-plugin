/*
 * Copyright (C) 2011 JFrog Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jfrog.hudson.release;

import com.google.common.collect.Maps;
import hudson.FilePath;
import hudson.model.FreeStyleProject;
import org.apache.commons.lang.StringUtils;
import org.jfrog.hudson.action.ActionableHelper;
import org.jfrog.hudson.gradle.ArtifactoryGradleConfigurator;
import org.jfrog.hudson.release.gradle.GradleReleaseWrapper;
import org.jfrog.hudson.util.PropertyUtils;
import org.kohsuke.stapler.StaplerRequest;

import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

/**
 * This action leads to execution of the release wrapper. It will collect information from the user about the release
 * and will trigger the release build. This action is not saved in the job xml file.
 *
 * @author Yossi Shaul
 */
public class GradleReleaseAction extends ReleaseAction {

    private final transient FreeStyleProject project;

    private transient Map<String, String> versionProps;
    private transient Map<String, String> additionalProps;
    /**
     * Map of release versions per module. Only used if versioning is per module
     */
    Map<String, String> releaseVersionPerModule;
    /**
     * Map of dev versions per module. Only used if versioning is per module
     */
    Map<String, String> nextVersionPerModule;

    Map<String, String> nextAdditionalValuePerModule;

    public GradleReleaseAction(FreeStyleProject project) {
        super(project);
        this.project = project;
    }

    public String[] getVersionProperties() {
        return getReleaseWrapper().getVersionPropsKeysList();
    }

    public String[] getAdditionalProperties() {
        return getReleaseWrapper().getAdditionalPropsKeysList();
    }


    private void init() {
        FilePath workspace = project.getSomeWorkspace();
        if (workspace == null) {
            throw new IllegalStateException("No workspace found, cannot perform staging");
        }
        FilePath gradlePropertiesPath = new FilePath(workspace, "gradle.properties");
        if (versionProps == null) {
            versionProps = PropertyUtils.getModulesPropertiesFromPropFile(gradlePropertiesPath, getVersionProperties());
        }
        if (additionalProps == null) {
            additionalProps =
                    PropertyUtils.getModulesPropertiesFromPropFile(gradlePropertiesPath, getAdditionalProperties());
        }
    }

    /**
     * Nullify the version properties map and the additional properties map, should be only called once the build is
     * <b>finished</b>.
     */
    public void reset() {
        versionProps = null;
        additionalProps = null;
    }

    /**
     * @return The release repository configured in Artifactory publisher.
     */
    @Override
    public String getDefaultStagingRepository() {
        ArtifactoryGradleConfigurator publisher = ActionableHelper.getBuildWrapper(
                project, ArtifactoryGradleConfigurator.class);
        if (publisher == null) {
            return null;
        }
        return publisher.getRepositoryKey();
    }

    /**
     * @return List of target repositories for deployment (release repositories first). Called from the UI.
     */
    @Override
    @SuppressWarnings({"UnusedDeclaration"})
    public List<String> getRepositoryKeys() {
        ArtifactoryGradleConfigurator artifactoryPublisher =
                ActionableHelper.getBuildWrapper(project, ArtifactoryGradleConfigurator.class);
        if (artifactoryPublisher != null) {
            return artifactoryPublisher.getArtifactoryServer().getReleaseRepositoryKeysFirst();
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public String getDefaultTagUrl() {
        String baseTagUrl = getReleaseWrapper().getTagPrefix();
        StringBuilder sb = new StringBuilder(baseTagUrl);
        //String releaseVersion = calculateReleaseVersion(releaseVersionPerModule.keySet().iterator().next());
        sb.append(releaseVersion);
        return sb.toString();
    }

    @Override
    public String getDefaultReleaseBranch() {
        String releaseBranchPrefix = getReleaseWrapper().getReleaseBranchPrefix();
        StringBuilder sb = new StringBuilder(StringUtils.trimToEmpty(releaseBranchPrefix));
        //String releaseVersion = calculateReleaseVersion(releaseVersionPerModule.keySet().iterator().next());
        sb.append(releaseVersion);
        return sb.toString();
    }

    @Override
    public String latestVersioningSelection() {
        return VERSIONING.PER_MODULE.name();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String calculateReleaseVersion() {
        return releaseVersion;
    }

    @Override
    protected void doPerModuleVersioning(StaplerRequest req) {
        releaseVersionPerModule = Maps.newHashMap();
        nextVersionPerModule = Maps.newHashMap();
        nextAdditionalValuePerModule = Maps.newHashMap();
        Enumeration params = req.getParameterNames();
        while (params.hasMoreElements()) {
            String key = (String) params.nextElement();
            if (key.startsWith("release.")) {
                releaseVersionPerModule.put(StringUtils.removeStart(key, "release."), req.getParameter(key));
            } else if (key.startsWith("next.")) {
                nextVersionPerModule.put(StringUtils.removeStart(key, "next."), req.getParameter(key));
            }
        }
    }

    public String getValueForProp(String prop) {
        init();
        return additionalProps.get(prop);
    }

    @Override
    public String calculateReleaseVersion(String fromVersion) {
        init();
        releaseVersion = super.calculateReleaseVersion(versionProps.get(fromVersion));
        return releaseVersion;
    }

    public String getReleaseVersionFor(String key) {
        switch (versioning) {
            case GLOBAL:
                return releaseVersion;
            case PER_MODULE:
                return releaseVersionPerModule.get(key);
            default:
                return null;
        }
    }

    public String getNextVersionFor(String key) {
        switch (versioning) {
            case GLOBAL:
                return nextVersion;
            case PER_MODULE:
                return nextVersionPerModule.get(key);
            default:
                return null;
        }
    }

    private GradleReleaseWrapper getReleaseWrapper() {
        return ActionableHelper.getBuildWrapper(project, GradleReleaseWrapper.class);
    }
}
