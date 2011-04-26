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
import org.jfrog.hudson.action.ActionableHelper;
import org.jfrog.hudson.gradle.ArtifactoryGradleConfigurator;
import org.jfrog.hudson.release.ReleaseAction;
import org.jfrog.hudson.release.scm.svn.SubversionManager;
import org.jfrog.hudson.util.PropertyUtils;
import org.kohsuke.stapler.StaplerRequest;

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
public class GradleReleaseAction extends ReleaseAction {

    private final transient FreeStyleProject project;

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
        super(project);
        this.project = project;
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
    public void init() {
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
     * @return The release repository configured in Artifactory publisher.
     */
    @Override
    public String getDefaultStagingRepository() {
        ArtifactoryGradleConfigurator publisher = getGradleWrapper();
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
        ArtifactoryGradleConfigurator configurator = getGradleWrapper();
        if (configurator != null) {
            return configurator.getArtifactoryServer().getReleaseRepositoryKeysFirst();
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public String lastStagingRepository() {
        ArtifactoryGradleConfigurator gradleWrapper = getGradleWrapper();
        return gradleWrapper == null ? null : gradleWrapper.getRepositoryKey();
    }

    @Override
    public String getDefaultTagUrl() {
        String baseTagUrl = getReleaseWrapper().getTagPrefix();
        StringBuilder sb = new StringBuilder(getBaseTagUrlAccordingToScm(baseTagUrl));
        String releaseVersion = getFirstReleaseVersion();
        sb.append(releaseVersion);
        return sb.toString();
    }

    @Override
    public String getDefaultReleaseBranch() {
        String releaseBranchPrefix = getReleaseWrapper().getReleaseBranchPrefix();
        StringBuilder sb = new StringBuilder(StringUtils.trimToEmpty(releaseBranchPrefix));
        String releaseVersion = getFirstReleaseVersion();
        sb.append(releaseVersion);
        return sb.toString();
    }

    @Override
    public String latestVersioningSelection() {
        return VERSIONING.PER_MODULE.name();
    }

    @Override
    public String getCurrentVersion() {
        String version = extractNumericVersion(releaseProps.values());
        if (StringUtils.isBlank(version)) {
            version = extractNumericVersion(nextIntegProps.values());
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

    @Override
    public String getDefaultTagComment() {
        return SubversionManager.COMMENT_PREFIX + "Creating release tag for version " + super.calculateReleaseVersion(
                getCurrentVersion());
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

    private GradleReleaseWrapper getReleaseWrapper() {
        return getGradleWrapper().getReleaseWrapper();
    }

    private ArtifactoryGradleConfigurator getGradleWrapper() {
        return ActionableHelper.getBuildWrapper(project, ArtifactoryGradleConfigurator.class);
    }

    private String getFirstReleaseVersion() {
        return super.calculateReleaseVersion(getCurrentVersion());
    }
}
