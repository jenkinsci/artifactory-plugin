/*
 * Copyright (C) 2010 JFrog Ltd.
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

package org.jfrog.hudson;

import hudson.Extension;
import hudson.maven.MavenModuleSetBuild;
import hudson.maven.reporters.MavenAbstractArtifactRecord;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.plugins.promoted_builds.Promotion;
import hudson.plugins.promoted_builds.PromotionProcess;
import net.sf.json.JSONObject;
import org.jfrog.hudson.util.Credentials;
import org.jfrog.hudson.util.IncludesExcludes;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * {@link ArtifactoryRedeployPublisher} altered for batch task
 * (promoted build) support.
 *
 * Based on promoted-build-plugin's
 * hudson.plugins.promoted_builds.tasks.RedeployBatchTaskPublisher class.
 */
public class ArtifactoryRedeployBatchTaskPublisher
extends ArtifactoryRedeployPublisher
{
    @DataBoundConstructor
    public ArtifactoryRedeployBatchTaskPublisher(ServerDetails details, boolean deployArtifacts,
            IncludesExcludes artifactDeploymentPatterns, Credentials overridingDeployerCredentials,
            boolean includeEnvVars, boolean deployBuildInfo, boolean evenIfUnstable, boolean runChecks,
            String violationRecipients, boolean includePublishArtifacts, String scopes,
            boolean disableLicenseAutoDiscovery, boolean discardOldBuilds, boolean passIdentifiedDownstream,
            boolean discardBuildArtifacts, String matrixParams) {
        super(details, deployArtifacts,
            artifactDeploymentPatterns, overridingDeployerCredentials,
            includeEnvVars, deployBuildInfo, evenIfUnstable, runChecks,
            violationRecipients, includePublishArtifacts, scopes,
            disableLicenseAutoDiscovery, discardOldBuilds, passIdentifiedDownstream,
            discardBuildArtifacts, matrixParams);
    }

    @Override
    protected MavenModuleSetBuild getMavenBuild(AbstractBuild<?,?> build) {
        return super.getMavenBuild(((Promotion) build).getTarget());
    }

    @Extension
    public static final class DescriptorImpl extends ArtifactoryRedeployPublisher.DescriptorImpl {
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return jobType == PromotionProcess.class;
        }

        @Override
        public ArtifactoryRedeployPublisher newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return req.bindJSON(ArtifactoryRedeployBatchTaskPublisher.class,formData);
        }
    }
}
