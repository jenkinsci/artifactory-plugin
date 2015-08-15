package org.jfrog.hudson.maven3;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.model.*;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.ListBoxModel;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jfrog.hudson.ArtifactoryServer;
import org.jfrog.hudson.ResolverOverrider;
import org.jfrog.hudson.ServerDetails;
import org.jfrog.hudson.VirtualRepository;
import org.jfrog.hudson.util.*;
import org.jfrog.hudson.util.plugins.PluginsUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.bind.JavaScriptMethod;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * A wrapper that takes over artifacts resolution and using the configured repository for resolution.<p/>
 * The {@link org.jfrog.hudson.maven3.Maven3ExtractorListener} is doing the heavy lifting. This class now just holds
 * the configuration.
 *
 * @author Tomer Cohen
 */
public class ArtifactoryMaven3NativeConfigurator extends BuildWrapper implements ResolverOverrider {

    private final ServerDetails details;
    /**
     * @deprecated: Use org.jfrog.hudson.maven3.ArtifactoryMaven3NativeConfigurator#getResolverCredentialsId()()
     */
    @Deprecated
    private Credentials overridingResolverCredentials;
    private final String deployerCredentialsId;

    @DataBoundConstructor
    public ArtifactoryMaven3NativeConfigurator(ServerDetails details, String deployerCredentialsId) {
        this.details = details;
        this.deployerCredentialsId = deployerCredentialsId;
    }

    public ServerDetails getDetails() {
        return details;
    }

    public String getDownloadReleaseRepositoryKey() {
        return details != null ? details.getResolveReleaseRepository().getRepoKey() : null;
    }

    public String getDownloadSnapshotRepositoryKey() {
        return details != null ? details.getResolveSnapshotRepositoryKey() : null;
    }

    public String getArtifactoryName() {
        return details != null ? details.artifactoryName : null;
    }

    @Override
    public Collection<? extends Action> getProjectActions(AbstractProject project) {
        return Collections.emptyList();
    }

    public boolean isOverridingDefaultResolver() {
        return StringUtils.isNotBlank(getResolverCredentialsId());
    }

    public Credentials getOverridingResolverCredentials() {
        return overridingResolverCredentials;
    }

    public String getResolverCredentialsId() {
        return deployerCredentialsId;
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

        /**
         * {@link org.jfrog.hudson.maven3.Maven3ExtractorListener} will populate the resolver context
         * */
        return new Environment() {
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

    public List<VirtualRepository> getVirtualRepositoryList(){
        return RepositoriesUtils.collectVirtualRepositories(getDescriptor().virtualRepositoryKeys, details.getResolveReleaseRepository().getKeyFromSelect());
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    private boolean isExtractorUsed(EnvVars env) {
        return Boolean.parseBoolean(env.get(ExtractorUtils.EXTRACTOR_USED));
    }

    @Extension
    public static class DescriptorImpl extends BuildWrapperDescriptor {
        private List<VirtualRepository> virtualRepositoryKeys;

        public DescriptorImpl() {
            super(ArtifactoryMaven3NativeConfigurator.class);
            load();
        }

        @Override
        public boolean isApplicable(AbstractProject<?, ?> item) {
            return MavenModuleSet.class.equals(item.getClass());
        }

        private void refreshVirtualRepositories(ArtifactoryServer artifactoryServer, String credentialsId)
                throws IOException {
            virtualRepositoryKeys = RepositoriesUtils.getVirtualRepositoryKeys(artifactoryServer.getUrl(),
                    credentialsId, artifactoryServer);
            Collections.sort(virtualRepositoryKeys);
        }

        /**
         * This method triggered from the client side by Ajax call.
         * The Element that trig is the "Refresh Repositories" button.
         *
         * @param url                           the artifactory url
         * @param credentialsId           credential id from the "Credential" plugin
         * @return {@link org.jfrog.hudson.util.RefreshServerResponse} object that represents the response of the repositories
         */
        @JavaScriptMethod
        public RefreshServerResponse refreshResolversFromArtifactory(String url, String credentialsId) {
            RefreshServerResponse response = new RefreshServerResponse();
            ArtifactoryServer artifactoryServer = RepositoriesUtils.getArtifactoryServer(url, getArtifactoryServers());

            try {
                refreshVirtualRepositories(artifactoryServer, credentialsId);
                response.setVirtualRepositories(virtualRepositoryKeys);
                response.setSuccess(true);
                return response;
            } catch (Exception e) {
                e.printStackTrace();
                response.setResponseMessage(e.getMessage());
                response.setSuccess(false);
            }

            /*
            * In case of Exception, we write the error in the Javascript scope!
            * */
            return response;
        }

        @SuppressWarnings("unused")
        public ListBoxModel doFillResolverCredentialsIdItems(@AncestorInPath Item project) {
            return PluginsUtils.fillPluginCredentials(project);
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
            return RepositoriesUtils.getArtifactoryServers();
        }
    }
}
