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

package org.jfrog.hudson.release.gradle;

import com.google.common.collect.Maps;
import hudson.FilePath;
import hudson.model.FreeStyleProject;
import hudson.scm.SCM;
import hudson.scm.SubversionSCM;
import org.apache.commons.lang.StringUtils;
import org.jfrog.hudson.ArtifactoryServer;
import org.jfrog.hudson.StagingPluginSettings;
import org.jfrog.hudson.gradle.ArtifactoryGradleConfigurator;
import org.jfrog.hudson.release.PromotionConfig;
import org.jfrog.hudson.release.ReleaseAction;
import org.jfrog.hudson.release.VcsConfig;
import org.jfrog.hudson.release.VersionedModule;
import org.jfrog.hudson.release.scm.svn.SubversionManager;
import org.jfrog.hudson.util.PropertyUtils;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

/**
 * {@inheritDoc} A release action which relates to Maven projects. All relevant information is taken from the {@code
 * gradle.properties} file which is related to the Gradle build.
 *
 * @author Tomer Cohen
 */
public class GradleReleaseAction extends ReleaseAction<FreeStyleProject, ArtifactoryGradleConfigurator> {

    private transient Map<String, String> releaseProps;
    private transient Map<String, String> nextIntegProps;
    /**
     * Map of release versions per module. Only used if versioning is per module
     */
    private Map<String, String> releaseVersionPerModule;
    /**
     * Map of dev versions per module. Only used if versioning is per module
     */
    private Map<String, String> nextVersionPerModule;

    public GradleReleaseAction(FreeStyleProject project) {
        super(project, ArtifactoryGradleConfigurator.class);
    }

    public String[] getReleaseProperties() {
        return getReleaseWrapper().getReleasePropsKeysList();
    }

    public String[] getNextIntegProperties() {
        return getReleaseWrapper().getNextIntegPropsKeysList();
    }

    /**
     * Initialize the version properties map from the gradle.properties file, and the additional properties from the
     * gradle.properties file.
     */
    public void init() throws IOException, InterruptedException {
        super.init();
        FilePath workspace = getRootLocationPath(project.getSomeWorkspace());
        if (workspace == null) {
            throw new IllegalStateException("No workspace found, cannot perform staging");
        }
        FilePath gradlePropertiesPath = new FilePath(workspace, "gradle.properties");
        if (releaseProps == null) {
            releaseProps = PropertyUtils.getModulesPropertiesFromPropFile(gradlePropertiesPath, getReleaseProperties());
        }
        if (nextIntegProps == null) {
            nextIntegProps =
                    PropertyUtils.getModulesPropertiesFromPropFile(gradlePropertiesPath, getNextIntegProperties());
        }
    }

    /**
     * Get the root path where the build is located, in case of {@link SubversionSCM} the project may be checked out to
     * a sub-directory from the root workspace location.
     *
     * @param workspace The root workspace of the project.
     * @return The location of the root of the Gradle build.
     */
    private FilePath getRootLocationPath(FilePath workspace) {
        SCM scm = project.getScm();
        if (scm instanceof SubversionSCM) {
            return new FilePath(workspace, ((SubversionSCM) scm).getLocations()[0].getLocalDir());
        }
        return workspace;
    }

    /**
     * Nullify the version properties map and the additional properties map, should be only called once the build is
     * <b>finished</b>. <p>Since the GradleReleaseAction is saved in memory and is only build when re-saving a project's
     * config or during startup, therefore a cleanup of the internal maps is needed.</p>
     */
    public void reset() {
        releaseProps = null;
        nextIntegProps = null;
        releaseVersion = null;
    }

    /**
     * @return List of target repositories for deployment (release repositories first). Called from the UI.
     */
    @Override
    @SuppressWarnings({"UnusedDeclaration"})
    public List<String> getRepositoryKeys() {
        ArtifactoryServer server = getArtifactoryServer();
        if (server != null) {
            return getArtifactoryServer().getReleaseRepositoryKeysFirst();
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public ArtifactoryServer getArtifactoryServer() {
        ArtifactoryGradleConfigurator configurator = getWrapper();
        if (configurator != null) {
            return configurator.getArtifactoryServer();
        }
        return null;
    }

    @Override
    public String latestVersioningSelection() {
        return VERSIONING.PER_MODULE.name();
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public String getValueForProp(String prop) {
        return nextIntegProps.get(prop);
    }

    @Override
    public String calculateReleaseVersion(String fromVersion) {
        String version = releaseProps.get(fromVersion);
        if (StringUtils.isBlank(version)) {
            version = nextIntegProps.get(fromVersion);
        }
        if (StringUtils.isNotBlank(version)) {
            return super.calculateReleaseVersion(version);
        }
        return "";
    }

    public String getCurrentVersionFor(String moduleName) {
        return releaseProps.get(moduleName);
    }

    @Override
    public String getReleaseVersionFor(Object moduleName) {
        switch (versioning) {
            case GLOBAL:
                return releaseVersion;
            case PER_MODULE:
                return releaseVersionPerModule.get(moduleName.toString());
            default:
                return null;
        }
    }

    @Override
    public String getNextVersionFor(Object moduleName) {
        switch (versioning) {
            case GLOBAL:
                return nextVersion;
            case PER_MODULE:
                return nextVersionPerModule.get(moduleName.toString());
            default:
                return null;
        }
    }

    @Override
    protected StagingPluginSettings getSelectedStagingPlugin() {
        return getReleaseWrapper().getStagingPlugin();
    }

    @Override
    protected void doPerModuleVersioning(StaplerRequest req) {
        releaseVersionPerModule = Maps.newHashMap();
        nextVersionPerModule = Maps.newHashMap();
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

    @Override
    protected void prepareBuilderSpecificDefaultGlobalModule() {
    }

    @Override
    protected void prepareBuilderSpecificDefaultModules() {
        defaultModules = Maps.newHashMap();

        for (String releaseProperties : getReleaseProperties()) {
            defaultModules.put(releaseProperties, new VersionedModule(releaseProperties,
                    calculateReleaseVersion(releaseProperties), null));
        }

        for (String nextIntegProperty : getNextIntegProperties()) {
            defaultModules.put(nextIntegProperty, new VersionedModule(nextIntegProperty,
                    calculateReleaseVersion(nextIntegProperty), calculateNextVersion(nextIntegProperty)));
        }
    }

    @Override
    protected void prepareBuilderSpecificDefaultVcsConfig() {
        String defaultReleaseBranch = getDefaultReleaseBranch();
        String defaultTagUrl = getDefaultTagUrl();
        defaultVcsConfig = new VcsConfig(StringUtils.isNotBlank(defaultReleaseBranch), defaultReleaseBranch,
                StringUtils.isNotBlank(defaultTagUrl), defaultTagUrl, getDefaultTagComment(),
                getDefaultNextDevelCommitMessage());
    }

    @Override
    protected void prepareBuilderSpecificDefaultPromotionConfig() {
        defaultPromotionConfig = new PromotionConfig(getDefaultStagingRepository(), null);
    }

    private GradleReleaseWrapper getReleaseWrapper() {
        return getWrapper().getReleaseWrapper();
    }

    private String getDefaultReleaseBranch() {
        String releaseBranchPrefix = getReleaseWrapper().getReleaseBranchPrefix();
        return new StringBuilder(StringUtils.trimToEmpty(releaseBranchPrefix)).append(getFirstReleaseVersion())
                .toString();
    }

    private String getDefaultTagUrl() {
        String baseTagUrl = getReleaseWrapper().getTagPrefix();
        return new StringBuilder(getBaseTagUrlAccordingToScm(baseTagUrl)).append(getFirstReleaseVersion()).toString();
    }

    private String getDefaultTagComment() {
        return new StringBuilder(SubversionManager.COMMENT_PREFIX).append("Release version ")
                .append(getFirstReleaseVersion()).toString();
    }

    private String getFirstReleaseVersion() {
        return super.calculateReleaseVersion(getCurrentVersion());
    }

    private String getCurrentVersion() {
        String version = extractNumericVersion(releaseProps.values());
        if (StringUtils.isBlank(version)) {
            version = extractNumericVersion(nextIntegProps.values());
        }
        if (StringUtils.isBlank(version)) {
            if (!releaseProps.values().isEmpty()) {
                version = releaseProps.values().iterator().next();
            } else if (!nextIntegProps.values().isEmpty()) {
                version = nextIntegProps.values().iterator().next();
            }
        }
        return version;
    }

    /**
     * Try to extract a numeric version from a collection of strings.
     *
     * @param versionStrings Collection of string properties.
     * @return The version string if exists in the collection.
     */
    private String extractNumericVersion(Collection<String> versionStrings) {
        if (versionStrings == null) {
            return "";
        }
        for (String value : versionStrings) {
            String releaseValue = calculateReleaseVersion(value);
            if (!releaseValue.equals(value)) {
                return releaseValue;
            }
        }
        return "";
    }

    /**
     * @return The release repository configured in Artifactory publisher.
     */
    private String getDefaultStagingRepository() {
        ArtifactoryGradleConfigurator publisher = getWrapper();
        if (publisher == null) {
            return null;
        }
        return publisher.getRepositoryKey();
    }
}
