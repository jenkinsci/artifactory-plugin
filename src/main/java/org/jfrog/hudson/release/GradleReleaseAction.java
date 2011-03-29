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
import hudson.FilePath;
import hudson.model.AbstractProject;
import hudson.model.BuildableItemWithBuildWrappers;
import org.apache.commons.lang.StringUtils;
import org.jfrog.hudson.action.ActionableHelper;
import org.jfrog.hudson.gradle.ArtifactoryGradleConfigurator;
import org.jfrog.hudson.release.gradle.GradleModule;
import org.jfrog.hudson.release.gradle.GradleReleaseWrapper;
import org.jfrog.hudson.util.PropertyUtils;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.util.Collection;
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

    private transient AbstractProject project;
    private Map<GradleModule, String> versions;
    private Map<GradleModule, String> additionalProps;
    private final String propKeys;
    private final String additionalPropKeys;
    /**
     * Map of release versions per module. Only used if versioning is per module
     */
    Map<GradleModule, String> releaseVersionPerModule;
    /**
     * Map of dev versions per module. Only used if versioning is per module
     */
    Map<GradleModule, String> nextVersionPerModule;

    Map<GradleModule, String> nextAdditionalValuePerModule;

    public GradleReleaseAction(AbstractProject project, Map<GradleModule, String> versions,
            Map<GradleModule, String> additionalProps, String propKeys, String additionalPropKeys) {
        //TODO: [by ys] use abstract project
        super(project);
        this.project = project;
        this.versions = versions;
        this.additionalProps = additionalProps;
        this.propKeys = propKeys;
        this.additionalPropKeys = additionalPropKeys;
    }

    public Collection<GradleModule> getVersionProperties() throws IOException {
        FilePath workspace = project.getSomeWorkspace();
        FilePath filePath = new FilePath(workspace, "gradle.properties");
        versions = PropertyUtils.getModulesPropertiesFromPropFile(filePath, propKeys);
        return versions.keySet();
    }

    public Collection<GradleModule> getAdditionalProperties() throws IOException {
        FilePath workspace = project.getSomeWorkspace();
        FilePath filePath = new FilePath(workspace, "gradle.properties");
        additionalProps = PropertyUtils.getModulesPropertiesFromPropFile(filePath, additionalPropKeys);
        return additionalProps.keySet();
    }

    /**
     * @return The release repository configured in Artifactory publisher.
     */
    @Override
    public String getDefaultStagingRepository() {
        ArtifactoryGradleConfigurator publisher = ActionableHelper.getBuildWrapper(
                (BuildableItemWithBuildWrappers) project, ArtifactoryGradleConfigurator.class);
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
                ActionableHelper
                        .getBuildWrapper((BuildableItemWithBuildWrappers) project, ArtifactoryGradleConfigurator.class);
        if (artifactoryPublisher != null) {
            return artifactoryPublisher.getArtifactoryServer().getReleaseRepositoryKeysFirst();
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public String getDefaultTagUrl() {
        GradleReleaseWrapper wrapper =
                ActionableHelper.getBuildWrapper((BuildableItemWithBuildWrappers) project, GradleReleaseWrapper.class);
        String baseTagUrl = wrapper.getTagPrefix();
        StringBuilder sb = new StringBuilder(baseTagUrl);
        String releaseVersion = calculateReleaseVersion(versions.keySet().iterator().next().getModuleVersion());
        sb.append(releaseVersion);
        return sb.toString();
    }

    @Override
    public String getDefaultReleaseBranch() {
        GradleReleaseWrapper wrapper =
                ActionableHelper.getBuildWrapper((BuildableItemWithBuildWrappers) project, GradleReleaseWrapper.class);
        String releaseBranchPrefix = wrapper.getReleaseBranchPrefix();
        StringBuilder sb = new StringBuilder(StringUtils.trimToEmpty(releaseBranchPrefix));
        String releaseVersion = calculateReleaseVersion(versions.keySet().iterator().next().getModuleVersion());
        sb.append(releaseVersion);
        return sb.toString();
    }

    @Override
    public String latestVersioningSelection() {
        return VERSIONING.PER_MODULE.name();
    }

    /**
     * {@inheritDoc}
     *
     * @return Nothing, since in Gradle there is no version for the root project like in Maven, this release version
     *         from the root project has no meaning.
     */
    @Override
    public String calculateReleaseVersion() {
        return "";
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
                releaseVersionPerModule
                        .put(new GradleModule(StringUtils.removeStart(key, "release."), req.getParameter(key)),
                                req.getParameter(key));
            } else if (key.startsWith("next.")) {
                nextVersionPerModule
                        .put(new GradleModule(StringUtils.removeStart(key, "next."), req.getParameter(key)),
                                req.getParameter(key));
            }
        }
    }

    public String getReleaseVersionFor(GradleModule moduleName) {
        switch (versioning) {
            case GLOBAL:
                return releaseVersion;
            case PER_MODULE:
                return releaseVersionPerModule.get(moduleName);
            default:
                return null;
        }
    }

    public String getNextVersionFor(GradleModule moduleName) {
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
