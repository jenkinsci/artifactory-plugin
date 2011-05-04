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

package org.jfrog.hudson.release.maven;

import com.google.common.collect.Maps;
import hudson.maven.MavenModule;
import hudson.maven.MavenModuleSet;
import hudson.maven.ModuleName;
import org.apache.commons.lang.StringUtils;
import org.jfrog.hudson.ArtifactoryRedeployPublisher;
import org.jfrog.hudson.ArtifactoryServer;
import org.jfrog.hudson.action.ActionableHelper;
import org.jfrog.hudson.release.ReleaseAction;
import org.kohsuke.stapler.StaplerRequest;

import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

/**
 * {@inheritDoc} A release action which relates to Maven projects. All relevant information is taken from {@link
 * MavenModuleSet}
 *
 * @author Tomer Cohen
 */
public class MavenReleaseAction extends ReleaseAction {
    private transient final MavenModuleSet project;

    /**
     * Map of release versions per module. Only used if versioning is per module
     */
    private Map<ModuleName, String> releaseVersionPerModule;
    /**
     * Map of dev versions per module. Only used if versioning is per module
     */
    private Map<ModuleName, String> nextVersionPerModule;

    public MavenReleaseAction(MavenModuleSet project) {
        super(project);
        this.project = project;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public Collection<MavenModule> getModules() {
        return project.getDisabledModules(false);
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public String getDefaultVersioning() {
        MavenReleaseWrapper wrapper = getReleaseWrapper();
        return wrapper.getDefaultVersioning();
    }

    @Override
    public String getDefaultReleaseBranch() {
        MavenReleaseWrapper wrapper = getReleaseWrapper();
        String releaseBranchPrefix = wrapper.getReleaseBranchPrefix();
        StringBuilder sb = new StringBuilder(StringUtils.trimToEmpty(releaseBranchPrefix));
        sb.append(getRootModule().getModuleName().artifactId).append("-").append(calculateReleaseVersion());
        return sb.toString();
    }

    @Override
    public String getDefaultTagUrl() {
        MavenReleaseWrapper wrapper = getReleaseWrapper();
        String baseTagUrl = wrapper.getTagPrefix();
        StringBuilder sb = new StringBuilder(getBaseTagUrlAccordingToScm(baseTagUrl));
        sb.append(getRootModule().getModuleName().artifactId).append("-").append(calculateReleaseVersion());
        return sb.toString();
    }

    @Override
    public String getDefaultStagingRepository() {
        ArtifactoryRedeployPublisher publisher = getPublisher();
        if (publisher == null) {
            return null;
        }
        return publisher.getRepositoryKey();
    }

    @Override
    public List<String> getRepositoryKeys() {
        ArtifactoryRedeployPublisher artifactoryPublisher = getPublisher();
        if (artifactoryPublisher != null) {
            return artifactoryPublisher.getArtifactoryServer().getReleaseRepositoryKeysFirst();
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public ArtifactoryServer getArtifactoryServer() {
        ArtifactoryRedeployPublisher artifactoryPublisher = getPublisher();
        if (artifactoryPublisher != null) {
            return artifactoryPublisher.getArtifactoryServer();
        }
        return null;
    }

    @Override
    public String lastStagingRepository() {
        ArtifactoryRedeployPublisher artifactoryPublisher = getPublisher();
        return artifactoryPublisher != null ? artifactoryPublisher.getRepositoryKey() : null;
    }

    @Override
    protected void doPerModuleVersioning(StaplerRequest req) {
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

    @Override
    public String getReleaseVersionFor(Object moduleName) {
        ModuleName mavenModuleName = (ModuleName) moduleName;
        switch (versioning) {
            case GLOBAL:
                return releaseVersion;
            case PER_MODULE:
                return releaseVersionPerModule.get(mavenModuleName);
            default:
                return null;
        }
    }

    @Override
    public String getNextVersionFor(Object moduleName) {
        ModuleName mavenModuleName = (ModuleName) moduleName;
        switch (versioning) {
            case GLOBAL:
                return nextVersion;
            case PER_MODULE:
                return nextVersionPerModule.get(mavenModuleName);
            default:
                return null;
        }
    }

    @Override
    public String getCurrentVersion() {
        return getRootModule().getVersion();
    }

    private MavenModule getRootModule() {
        return project.getRootModule();
    }

    private MavenReleaseWrapper getReleaseWrapper() {
        return ActionableHelper.getBuildWrapper(project, MavenReleaseWrapper.class);
    }

    private ArtifactoryRedeployPublisher getPublisher() {
        return ActionableHelper.getPublisher(project, ArtifactoryRedeployPublisher.class);
    }

}
