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

package org.jfrog.hudson.release.maven;

import hudson.model.AbstractBuild;
import hudson.security.Permission;
import org.jfrog.hudson.ArtifactoryPlugin;
import org.jfrog.hudson.ArtifactoryRedeployPublisher;
import org.jfrog.hudson.ArtifactoryServer;
import org.jfrog.hudson.action.ActionableHelper;
import org.jfrog.hudson.release.PromoteBuildAction;
import org.jfrog.hudson.util.CredentialResolver;
import org.jfrog.hudson.util.Credentials;

import java.util.Collections;
import java.util.List;

/**
 * @author Tomer Cohen
 */
public class MavenPromoteBuildAction extends PromoteBuildAction {

    public MavenPromoteBuildAction(AbstractBuild build) {
        super(build);
    }

    @Override
    public List<String> getRepositoryKeys() {
        ArtifactoryRedeployPublisher artifactoryPublisher = ActionableHelper.getPublisher(
                getBuild().getProject(), ArtifactoryRedeployPublisher.class);
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
        ArtifactoryRedeployPublisher artifactoryPublisher = ActionableHelper.getPublisher(
                getBuild().getProject(), ArtifactoryRedeployPublisher.class);
        return artifactoryPublisher.getArtifactoryServer();
    }

    @Override
    protected Credentials getCredentials(ArtifactoryServer server) {
        ArtifactoryRedeployPublisher artifactoryPublisher = ActionableHelper.getPublisher(
                getBuild().getProject(), ArtifactoryRedeployPublisher.class);
        Credentials deployer = CredentialResolver.getPreferredDeployer(artifactoryPublisher, server);
        return deployer;
    }

    @Override
    protected Permission getPermission() {
        return ArtifactoryPlugin.PROMOTE;
    }
}
