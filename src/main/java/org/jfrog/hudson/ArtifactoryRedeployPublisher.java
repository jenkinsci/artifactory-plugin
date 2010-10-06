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
import hudson.Launcher;
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.maven.reporters.MavenAbstractArtifactRecord;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import hudson.util.Scrambler;
import net.sf.json.JSONObject;
import org.jfrog.build.client.ArtifactoryBuildInfoClient;
import org.jfrog.hudson.action.ArtifactoryProjectAction;
import org.jfrog.hudson.maven2.ArtifactsDeployer;
import org.jfrog.hudson.maven2.BuildInfoDeployer;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import java.io.IOException;
import java.util.List;

/**
 * {@link Publisher} for {@link hudson.maven.MavenModuleSetBuild} to deploy artifacts to Artifactory only after a build
 * is fully succeeded. `
 *
 * @author Yossi Shaul
 */
public class ArtifactoryRedeployPublisher extends Recorder {
    /**
     * Repository URL and repository to deploy artifacts to.
     */
    private final ServerDetails details;
    /**
     * If checked (default) deploy maven artifacts
     */
    private final boolean deployArtifacts;
    private final String username;
    private final String scrambledPassword;
    /**
     * Include environment variables in the generated build info
     */
    private final boolean includeEnvVars;
    private final boolean skipBuildInfoDeploy;

    /**
     * Deploy even if the build is unstable (failed tests)
     */
    public final boolean evenIfUnstable;

    private final boolean runChecks;

    private final String violationRecipients;


    @DataBoundConstructor
    public ArtifactoryRedeployPublisher(ServerDetails details,
                                        boolean deployArtifacts, String username, String password,
                                        boolean includeEnvVars, boolean deployBuildInfo, boolean evenIfUnstable,
                                        boolean runChecks, String violationRecipients) {
        this.details = details;
        this.username = username;
        this.includeEnvVars = includeEnvVars;
        this.deployArtifacts = deployArtifacts;
        this.evenIfUnstable = evenIfUnstable;
        this.runChecks = runChecks;
        this.violationRecipients = violationRecipients;
        this.skipBuildInfoDeploy = !deployBuildInfo;

        this.scrambledPassword = Scrambler.scramble(password);

        /*DescriptorExtensionList<Publisher, Descriptor<Publisher>> descriptors = Publisher.all();
        Descriptor<Publisher> redeployPublisher = descriptors.find(RedeployPublisher.DescriptorImpl.class.getName());
        if (redeployPublisher != null) {
            descriptors.remove(redeployPublisher);
        }*/
    }

    // NOTE: The following getters are used by jelly. Do not remove them

    @SuppressWarnings({"UnusedDeclaration"})
    public boolean isDeployArtifacts() {
        return deployArtifacts;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public boolean isSkipBuildInfoDeploy() {
        return skipBuildInfoDeploy;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public boolean isEvenIfUnstable() {
        return evenIfUnstable;
    }

    public boolean isIncludeEnvVars() {
        return includeEnvVars;
    }

    public boolean isRunChecks() {
        return runChecks;
    }

    public String getUsername() {
        return username;
    }

    public String getViolationRecipients() {
        return violationRecipients;
    }

    public String getPassword() {
        return Scrambler.descramble(scrambledPassword);
    }

    public String getArtifactoryName() {
        return details != null ? details.artifactoryName : null;
    }

    /**
     * @return The release versions deployment repository.
     */
    public String getRepositoryKey() {
        return details != null ? details.repositoryKey : null;
    }

    /**
     * @return The snapshots deployment repository. If not defined the releases deployment repository will be returned
     */
    public String getSnapshotsRepositoryKey() {
        return details != null ?
                (details.snapshotsRepositoryKey != null ? details.snapshotsRepositoryKey : details.repositoryKey) :
                null;
    }

    @Override
    public Action getProjectAction(AbstractProject<?, ?> project) {
        return details != null ? new ArtifactoryProjectAction(details.artifactoryName, project) : null;
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener)
            throws InterruptedException, IOException {
        if (build.getResult().isWorseThan(getTreshold())) {
            return true;    // build failed. Don't publish
        }

        if (getArtifactoryServer() == null) {
            listener.getLogger().format("No Artifactory server configured for %s. " +
                    "Please check your configuration.", getArtifactoryName()).println();
            build.setResult(Result.FAILURE);
            return true;
        }

        MavenAbstractArtifactRecord mar = getAction(build);
        if (mar == null) {
            listener.getLogger().println("No artifacts are recorded. Is this a Maven project?");
            build.setResult(Result.FAILURE);
            return true;
        }

        ArtifactoryServer server = getArtifactoryServer();
        ArtifactoryBuildInfoClient client = server.createArtifactoryClient(getUsername(), getPassword());
        MavenModuleSetBuild mavenBuild = (MavenModuleSetBuild) build;
        try {
            verifySupportedArtifactoryVersion(client);
            if (deployArtifacts) {
                new ArtifactsDeployer(this, client, mavenBuild, mar, listener).deploy();
            }
            if (!skipBuildInfoDeploy) {
                new BuildInfoDeployer(this, client, mavenBuild, listener).deploy();
                // add the result action
                build.getActions().add(new BuildInfoResultAction(getArtifactoryName(), build));
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace(listener.error(e.getMessage()));
        } finally {
            client.shutdown();
        }

        // failed
        build.setResult(Result.FAILURE);
        return true;
    }

    private void verifySupportedArtifactoryVersion(ArtifactoryBuildInfoClient client) throws Exception {
        // get the version of artifactory, if it is an unsupported version, an UnsupportedOperationException
        // will be thrown, and no deployment will commence.
        client.verifyCompatibleArtifactoryVersion();
    }

    /**
     * Obtains the {@link MavenAbstractArtifactRecord} that we'll work on.
     * <p/>
     * This allows promoted-builds plugin to reuse the code for delayed deployment.
     */
    protected MavenAbstractArtifactRecord getAction(AbstractBuild<?, ?> build) {
        return build.getAction(MavenAbstractArtifactRecord.class);
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    public ArtifactoryServer getArtifactoryServer() {
        List<ArtifactoryServer> servers = getDescriptor().getArtifactoryServers();
        for (ArtifactoryServer server : servers) {
            if (server.getName().equals(getArtifactoryName())) {
                return server;
            }
        }
        return null;
    }

    private Result getTreshold() {
        if (evenIfUnstable) {
            return Result.UNSTABLE;
        } else {
            return Result.SUCCESS;
        }
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        public DescriptorImpl() {
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return jobType == MavenModuleSet.class;
        }

        @Override
        public ArtifactoryRedeployPublisher newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return req.bindJSON(ArtifactoryRedeployPublisher.class, formData);
        }

        @Override
        public String getDisplayName() {
            return "Deploy artifacts to Artifactory";
            //return Messages.RedeployPublisher_getDisplayName();
        }

        public FormValidation doCheckViolationRecipients(@QueryParameter String value) {
            try {
                new InternetAddress(value);
                return FormValidation.ok();
            } catch (AddressException e) {
                return FormValidation.error(e.getMessage());
            }
        }

        /**
         * Returns the list of {@link ArtifactoryServer} configured.
         *
         * @return can be empty but never null.
         */
        public List<ArtifactoryServer> getArtifactoryServers() {
            ArtifactoryBuilder.DescriptorImpl descriptor = (ArtifactoryBuilder.DescriptorImpl)
                    Hudson.getInstance().getDescriptor(ArtifactoryBuilder.class);
            return descriptor.getArtifactoryServers();
        }
    }

}
