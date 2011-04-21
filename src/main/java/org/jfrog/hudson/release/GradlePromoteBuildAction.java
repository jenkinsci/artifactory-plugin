/*
 * Copyright (C) 2011 JFrog Ltd.
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

import hudson.model.AbstractBuild;
import hudson.model.BuildableItemWithBuildWrappers;
import hudson.security.Permission;
import org.jfrog.hudson.ArtifactoryPlugin;
import org.jfrog.hudson.ArtifactoryServer;
import org.jfrog.hudson.action.ActionableHelper;
import org.jfrog.hudson.gradle.ArtifactoryGradleConfigurator;
import org.jfrog.hudson.util.CredentialResolver;
import org.jfrog.hudson.util.Credentials;

import java.util.Collections;
import java.util.List;

/**
 * A stage action for Gradle builds, allows to stage and release builds in Artifactory.
 *
 * @author Tomer Cohen
 */
public class GradlePromoteBuildAction extends PromoteBuildAction {

    public GradlePromoteBuildAction(AbstractBuild build) {
        super(build);
    }

    @Override
    protected Permission getPermission() {
        return ArtifactoryPlugin.PROMOTE;
    }


    /**
     * @return List of target repositories for deployment (release repositories first). Called from the UI.
     */
    @Override
    @SuppressWarnings({"UnusedDeclaration"})
    public List<String> getRepositoryKeys() {
        ArtifactoryGradleConfigurator artifactoryPublisher = ActionableHelper.getBuildWrapper(
                (BuildableItemWithBuildWrappers) getBuild().getProject(), ArtifactoryGradleConfigurator.class);
        if (artifactoryPublisher != null) {
            List<String> repos = artifactoryPublisher.getArtifactoryServer().getReleaseRepositoryKeysFirst();
            repos.add(0, "");  // option not to move
            return repos;
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    protected ArtifactoryServer getArtifactoryServer() {
        ArtifactoryGradleConfigurator wrapper = ActionableHelper
                .getBuildWrapper((BuildableItemWithBuildWrappers) getBuild().getProject(),
                        ArtifactoryGradleConfigurator.class);
        return wrapper.getArtifactoryServer();
    }

    @Override
    protected Credentials getCredentials(ArtifactoryServer server) {
        ArtifactoryGradleConfigurator wrapper = ActionableHelper
                .getBuildWrapper((BuildableItemWithBuildWrappers) getBuild().getProject(),
                        ArtifactoryGradleConfigurator.class);
        return CredentialResolver.getPreferredDeployer(wrapper, server);
    }
}

