package org.jfrog.hudson.maven3;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Hudson;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import net.sf.json.JSONObject;
import org.jfrog.hudson.ArtifactoryBuilder;
import org.jfrog.hudson.ArtifactoryServer;
import org.jfrog.hudson.ResolverOverrider;
import org.jfrog.hudson.ServerDetails;
import org.jfrog.hudson.util.Credentials;
import org.jfrog.hudson.util.ExtractorUtils;
import org.jfrog.hudson.util.MavenVersionHelper;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A wrapper that takes over artifacts resolution and using the configured repository for resolution.<p/>
 * The {@link org.jfrog.hudson.maven3.Maven3ExtractorListener} is doing the heavy lifting. This class now just holds
 * the configuration.
 *
 * @author Tomer Cohen
 */
public class ArtifactoryMaven3NativeConfigurator extends BuildWrapper implements ResolverOverrider {

    private final ServerDetails details;
    private final Credentials overridingResolverCredentials;

    @DataBoundConstructor
    public ArtifactoryMaven3NativeConfigurator(ServerDetails details, Credentials overridingResolverCredentials) {
        this.details = details;
        this.overridingResolverCredentials = overridingResolverCredentials;
    }

    public ServerDetails getDetails() {
        return details;
    }

    public String getDownloadRepositoryKey() {
        return details != null ? details.downloadRepositoryKey : null;
    }

    public String getArtifactoryName() {
        return details != null ? details.artifactoryName : null;
    }

    @Override
    public Collection<? extends Action> getProjectActions(AbstractProject project) {
        return Collections.emptyList();
    }

    public boolean isOverridingDefaultResolver() {
        return getOverridingResolverCredentials() != null;
    }

    public Credentials getOverridingResolverCredentials() {
        return overridingResolverCredentials;
    }

    @Override
    public Environment setUp(final AbstractBuild build, Launcher launcher, BuildListener listener)
            throws IOException, InterruptedException {
        if (!(build instanceof MavenModuleSetBuild)) {
            return new Environment() {
            };
        }

        EnvVars envVars = build.getEnvironment(listener);
        boolean supportedMavenVersion =
                MavenVersionHelper.isAtLeastResolutionCapableVersion((MavenModuleSetBuild) build, envVars, listener);
        if (!supportedMavenVersion) {
            listener.getLogger().println("Artifactory resolution is not active. Maven 3.0.2 or higher is required to " +
                    "force resolution from Artifactory.");
            return new Environment() {
            };
        }

        return new Environment() {
            @Override
            public void buildEnvVars(Map<String, String> env) {
                super.buildEnvVars(env);
            }

            @Override
            public boolean tearDown(AbstractBuild build, BuildListener listener)
                    throws IOException, InterruptedException {
                return super.tearDown(build, listener);
            }
        };
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

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    private boolean isExtractorUsed(EnvVars env) {
        return Boolean.parseBoolean(env.get(ExtractorUtils.EXTRACTOR_USED));
    }

    @Extension(optional = true)
    public static class DescriptorImpl extends BuildWrapperDescriptor {
        public DescriptorImpl() {
            super(ArtifactoryMaven3NativeConfigurator.class);
            load();
        }

        @Override
        public boolean isApplicable(AbstractProject<?, ?> item) {
            return MavenModuleSet.class.equals(item.getClass());
        }

        @Override
        public String getDisplayName() {
            return "Resolve artifacts from Artifactory";
        }

        @Override
        public String getHelpFile() {
            return "/plugin/artifactory/help/ArtifactoryMaven3NativeConfigurator/help.html";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            req.bindParameters(this, "maven");
            save();
            return true;
        }

        /**
         * Returns the list of {@link org.jfrog.hudson.ArtifactoryServer} configured.
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
