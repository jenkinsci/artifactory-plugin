package org.jfrog.hudson;

import hudson.Extension;
import hudson.Launcher;
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.maven.reporters.MavenAbstractArtifactRecord;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Hudson;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.Scrambler;
import net.sf.json.JSONObject;
import org.jfrog.build.client.ArtifactoryBuildInfoClient;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.util.List;

/**
 * {@link Publisher} for {@link hudson.maven.MavenModuleSetBuild} to deploy artifacts to Artifactory only after a build
 * is fully succeeded.
 *
 * @author Yossi Shaul
 */
public class ArtifactoryRedeployPublisher extends Recorder {
    /**
     * Repository URL and repository to deploy artifacts to.
     */
    private final ServerDetails details;
    private final boolean deployArtifacts;
    private final String username;
    private final String scrambledPassword;
    private final boolean includeEnvVars;

    @DataBoundConstructor
    public ArtifactoryRedeployPublisher(ServerDetails details,
            boolean deployArtifacts, String username, String password, boolean includeEnvVars) {
        this.details = details;
        this.deployArtifacts = deployArtifacts;
        this.username = username;
        this.includeEnvVars = includeEnvVars;
        this.scrambledPassword = Scrambler.scramble(password);

        /*DescriptorExtensionList<Publisher, Descriptor<Publisher>> descriptors = Publisher.all();
        Descriptor<Publisher> redeployPublisher = descriptors.find(RedeployPublisher.DescriptorImpl.class.getName());
        if (redeployPublisher != null) {
            descriptors.remove(redeployPublisher);
        }*/
    }

    public boolean isDeployArtifacts() {
        return deployArtifacts;
    }

    public boolean isIncludeEnvVars() {
        return includeEnvVars;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return Scrambler.descramble(scrambledPassword);
    }

    public String getArtifactoryName() {
        return details != null ? details.artifactoryName : null;
    }

    public String getRepositoryKey() {
        return details != null ? details.repositoryKey : null;
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener)
            throws InterruptedException, IOException {
        if (build.getResult().isWorseThan(Result.SUCCESS)) {
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
            // get the version of artifactory, if it is an unsupported version, an UnsupportedOperationException
            // will be thrown, and no deployment will commence.
            client.getVersion();
            if (deployArtifacts) {
                new ArtifactsDeployer(this, client, mavenBuild, mar, listener).deploy();
            }
            new BuildInfoDeployer(this, client, mavenBuild, listener).deploy();
            // add the result action
            build.getActions().add(new BuildInfoResultAction(this, build));
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
