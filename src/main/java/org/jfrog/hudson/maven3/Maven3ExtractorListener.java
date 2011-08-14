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

package org.jfrog.hudson.maven3;

import hudson.Extension;
import hudson.Launcher;
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Environment;
import hudson.model.listeners.RunListener;
import org.apache.commons.lang.StringUtils;
import org.jfrog.hudson.ArtifactoryRedeployPublisher;
import org.jfrog.hudson.ServerDetails;
import org.jfrog.hudson.action.ActionableHelper;
import org.jfrog.hudson.maven3.extractor.MavenExtractorEnvironment;
import org.jfrog.hudson.release.ReleaseAction;
import org.jfrog.hudson.util.BuildContext;

import java.io.IOException;

/**
 * Build listener that sets up an environment for a native Maven build. If a native Maven build is configured and
 * archiving is <b>disabled</b> then the external extractor is used with the environment that was taken from the
 * publisher.
 *
 * @author Tomer Cohen
 */
@Extension
public class Maven3ExtractorListener extends RunListener<AbstractBuild> {

    @Override
    public Environment setUpEnvironment(final AbstractBuild build, Launcher launcher, final BuildListener listener)
            throws IOException, InterruptedException {
        // if not a native maven project return empty env.
        if (!(build instanceof MavenModuleSetBuild)) {
            return new Environment() {
            };
        }

        final MavenModuleSet project = (MavenModuleSet) build.getProject();
        final ArtifactoryRedeployPublisher publisher = project.getPublishers().get(ArtifactoryRedeployPublisher.class);
        // if the artifactory publisher is not active, return empty env.
        if (publisher == null) {
            return new Environment() {
            };
        }
        return new MavenExtractorEnvironment((MavenModuleSetBuild) build, publisher,
                createBuildContextFromPublisher(publisher, build), listener);
    }

    private BuildContext createBuildContextFromPublisher(ArtifactoryRedeployPublisher publisher, AbstractBuild build) {
        ReleaseAction release = ActionableHelper.getLatestAction(build, ReleaseAction.class);
        ServerDetails server = publisher.getDetails();
        if (release != null) {
            // staging build might change the target deployment repository
            String stagingRepoKey = release.getStagingRepositoryKey();
            if (!StringUtils.isBlank(stagingRepoKey) && !stagingRepoKey.equals(server.repositoryKey)) {
                server = new ServerDetails(server.artifactoryName, stagingRepoKey,
                        server.snapshotsRepositoryKey, server.downloadRepositoryKey);
            }
        }

        BuildContext context = new BuildContext(server, publisher, publisher.isRunChecks(),
                publisher.isIncludePublishArtifacts(), publisher.getViolationRecipients(), publisher.getScopes(),
                publisher.isLicenseAutoDiscovery(), publisher.isDiscardOldBuilds(), publisher.isDeployArtifacts(),
                publisher.getArtifactDeploymentPatterns(), !publisher.isDeployBuildInfo(),
                publisher.isIncludeEnvVars(), publisher.isDiscardBuildArtifacts(), publisher.getMatrixParams());
        context.setEvenIfUnstable(publisher.isEvenIfUnstable());
        return context;
    }
}
