package org.jfrog.hudson.generic;

import com.google.common.collect.Lists;
import com.tikal.jenkins.plugins.multijob.MultiJobProject;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixProject;
import hudson.model.*;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.XStream2;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jfrog.build.api.Artifact;
import org.jfrog.build.api.Dependency;
import org.jfrog.build.api.dependency.BuildDependency;
import org.jfrog.build.client.ProxyConfiguration;
import org.jfrog.build.extractor.clientConfiguration.client.ArtifactoryBuildInfoClient;
import org.jfrog.build.extractor.clientConfiguration.client.ArtifactoryDependenciesClient;
import org.jfrog.hudson.*;
import org.jfrog.hudson.BintrayPublish.BintrayPublishAction;
import org.jfrog.hudson.action.ActionableHelper;
import org.jfrog.hudson.release.promotion.UnifiedPromoteBuildAction;
import org.jfrog.hudson.util.*;
import org.jfrog.hudson.util.converters.DeployerResolverOverriderConverter;
import org.jfrog.hudson.util.plugins.MultiConfigurationUtils;
import org.jfrog.hudson.util.plugins.PluginsUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.bind.JavaScriptMethod;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Freestyle Generic configurator
 *
 * @author Shay Yaakov
 */
public class ArtifactoryGenericConfigurator extends BuildWrapper implements DeployerOverrider, ResolverOverrider,
        BuildInfoAwareConfigurator, MultiConfigurationAware {

    private final ServerDetails details;
    private final ServerDetails resolverDetails;
    private final CredentialsConfig deployerCredentialsConfig;
    private final CredentialsConfig resolverCredentialsConfig;
    private final boolean useSpecs;
    private final SpecConfiguration uploadSpec;
    private final SpecConfiguration downloadSpec;
    private final String deployPattern;
    private final String resolvePattern;
    private final String matrixParams;
    private final boolean deployBuildInfo;
    /**
     * Include environment variables in the generated build info
     */
    private final boolean includeEnvVars;
    private final IncludesExcludes envVarsPatterns;
    private final boolean discardOldBuilds;
    private final boolean discardBuildArtifacts;
    private final boolean asyncBuildRetention;
    private transient List<Dependency> publishedDependencies;
    private transient List<BuildDependency> buildDependencies;
    private String artifactoryCombinationFilter;
    private boolean multiConfProject;
    private String customBuildName;
    private boolean overrideBuildName;

    /**
     * @deprecated: Use org.jfrog.hudson.generic.ArtifactoryGenericConfigurator#getDeployerCredentials()()
     */
    @Deprecated
    private Credentials overridingDeployerCredentials;
    /**
     * @deprecated: Use org.jfrog.hudson.generic.ArtifactoryGenericConfigurator#getResolverCredentialsId()()
     */
    @Deprecated
    private Credentials overridingResolverCredentials;

    @DataBoundConstructor
    public ArtifactoryGenericConfigurator(ServerDetails details, ServerDetails resolverDetails,
                                          CredentialsConfig deployerCredentialsConfig, CredentialsConfig resolverCredentialsConfig,
                                          String deployPattern, String resolvePattern, String matrixParams,
                                          boolean useSpecs, SpecConfiguration uploadSpec, SpecConfiguration downloadSpec,
                                          boolean deployBuildInfo,
                                          boolean includeEnvVars, IncludesExcludes envVarsPatterns,
                                          boolean discardOldBuilds,
                                          boolean discardBuildArtifacts,
                                          boolean asyncBuildRetention,
                                          boolean multiConfProject,
                                          String artifactoryCombinationFilter,
                                          String customBuildName,
                                          boolean overrideBuildName) {
        this.details = details;
        this.resolverDetails = resolverDetails;
        this.deployerCredentialsConfig = deployerCredentialsConfig;
        this.resolverCredentialsConfig = resolverCredentialsConfig;
        this.deployPattern = deployPattern;
        this.resolvePattern = resolvePattern;
        this.useSpecs = useSpecs;
        this.uploadSpec = uploadSpec;
        this.downloadSpec = downloadSpec;
        this.matrixParams = matrixParams;
        this.deployBuildInfo = deployBuildInfo;
        this.includeEnvVars = includeEnvVars;
        this.envVarsPatterns = envVarsPatterns;
        this.discardOldBuilds = discardOldBuilds;
        this.discardBuildArtifacts = discardBuildArtifacts;
        this.asyncBuildRetention = asyncBuildRetention;
        this.multiConfProject = multiConfProject;
        this.artifactoryCombinationFilter = artifactoryCombinationFilter;
        this.customBuildName = customBuildName;
        this.overrideBuildName = overrideBuildName;
    }

    public String getArtifactoryName() {
        return details != null ? details.artifactoryName : null;
    }

    public String getArtifactoryResolverName() {
        return resolverDetails != null ? resolverDetails.artifactoryName : null;
    }

    public String getArtifactoryUrl() {
        ArtifactoryServer server = getArtifactoryServer();
        return server != null ? server.getUrl() : null;
    }

    public boolean isOverridingDefaultDeployer() {
        return deployerCredentialsConfig != null && deployerCredentialsConfig.isCredentialsProvided();
    }

    public String getRepositoryKey() {
        return details.getDeployReleaseRepository().getRepoKey();
    }

    public String getDefaultPromotionTargetRepository() {
        //Not implemented
        return null;
    }

    public ServerDetails getDetails() {
        return details;
    }

    public ServerDetails getResolverDetails() {
        return resolverDetails;
    }

    public Credentials getOverridingDeployerCredentials() {
        return overridingDeployerCredentials;
    }

    public CredentialsConfig getDeployerCredentialsConfig() {
        return deployerCredentialsConfig;
    }

    public String getDeployPattern() {
        return deployPattern;
    }

    public String getResolvePattern() {
        return resolvePattern;
    }

    public boolean isUseSpecs() {
        return useSpecs;
    }

    public SpecConfiguration getUploadSpec() {
        return uploadSpec;
    }

    public SpecConfiguration getDownloadSpec() {
        return downloadSpec;
    }

    public String getMatrixParams() {
        return matrixParams;
    }

    public boolean isDeployBuildInfo() {
        return deployBuildInfo;
    }

    public boolean isIncludeEnvVars() {
        return includeEnvVars;
    }

    public IncludesExcludes getEnvVarsPatterns() {
        return envVarsPatterns;
    }

    public boolean isRunChecks() {
        // There is no use of license checks in a generic build
        return false;
    }

    public String getViolationRecipients() {
        return null;
    }

    public boolean isIncludePublishArtifacts() {
        return false;
    }

    public String getScopes() {
        return null;
    }

    public boolean isLicenseAutoDiscovery() {
        return false;
    }

    public boolean isDiscardOldBuilds() {
        return discardOldBuilds;
    }

    public boolean isDiscardBuildArtifacts() {
        return discardBuildArtifacts;
    }

    public boolean isAsyncBuildRetention() {
        return asyncBuildRetention;
    }

    public boolean isEnableIssueTrackerIntegration() {
        return false;
    }

    public boolean isAggregateBuildIssues() {
        return false;
    }

    public String getAggregationBuildStatus() {
        return null;
    }

    public boolean isBlackDuckRunChecks() {
        return false;
    }

    public String getBlackDuckAppName() {
        return null;
    }

    public String getBlackDuckAppVersion() {
        return null;
    }

    public String getBlackDuckReportRecipients() {
        return null;
    }

    public String getBlackDuckScopes() {
        return null;
    }

    public boolean isBlackDuckIncludePublishedArtifacts() {
        return false;
    }

    public boolean isAutoCreateMissingComponentRequests() {
        return false;
    }

    public boolean isAutoDiscardStaleComponentRequests() {
        return false;
    }

    public String getArtifactoryCombinationFilter() {
        return artifactoryCombinationFilter;
    }

    public boolean isMultiConfProject() {
        return multiConfProject;
    }

    public String getCustomBuildName() {
        return customBuildName;
    }

    public boolean isOverrideBuildName() {
        return overrideBuildName;
    }

    public ArtifactoryServer getArtifactoryServer() {
        return RepositoriesUtils.getArtifactoryServer(getArtifactoryName(), getDescriptor().getArtifactoryServers());
    }

    public ArtifactoryServer getArtifactoryResolverServer() {
        return RepositoriesUtils.getArtifactoryServer(getArtifactoryResolverName(),
                getDescriptor().getArtifactoryServers());
    }

    public List<Repository> getReleaseRepositoryList() {
        if (details.getDeploySnapshotRepository() == null) {
            return Lists.newArrayList();
        }
        return RepositoriesUtils.collectRepositories(details.getDeploySnapshotRepository().getKeyFromSelect());
    }

    @Override
    public Collection<? extends Action> getProjectActions(AbstractProject project) {
        if (isOverrideBuildName()) {
            return ActionableHelper.getArtifactoryProjectAction(getArtifactoryName(), project, getCustomBuildName());
        } else {
            return ActionableHelper.getArtifactoryProjectAction(getArtifactoryName(), project);
        }
    }

    @Override
    public Environment setUp(final AbstractBuild build, Launcher launcher, BuildListener listener)
            throws IOException, InterruptedException {
        listener.getLogger().println("Jenkins Artifactory Plugin version: " + ActionableHelper.getArtifactoryPluginVersion());
        RepositoriesUtils.validateServerConfig(build, listener, getArtifactoryServer(), getArtifactoryUrl());

        if (StringUtils.isBlank(getArtifactoryName())) {
            return super.setUp(build, launcher, listener);
        }

        // Resolve process:
        ArtifactoryServer resolverServer = getArtifactoryResolverServer();
        CredentialsConfig preferredResolver = CredentialManager.getPreferredResolver(ArtifactoryGenericConfigurator.this,
                resolverServer);
        String username = preferredResolver.provideUsername(build.getProject());
        String password = preferredResolver.providePassword(build.getProject());

        ProxyConfiguration proxyConfiguration = null;
        hudson.ProxyConfiguration proxy = Jenkins.getInstance().proxy;
        if (proxy != null && !resolverServer.isBypassProxy()) {
            proxyConfiguration = ArtifactoryServer.createProxyConfiguration(proxy);
        }

        ArtifactoryDependenciesClient dependenciesClient = null;
        try {
            if (isUseSpecs()) {
                String spec = SpecUtils.getSpecStringFromSpecConf(downloadSpec, build.getEnvironment(listener),
                        build.getExecutor().getCurrentWorkspace(), listener.getLogger());
                FilePath workspace = build.getExecutor().getCurrentWorkspace();
                publishedDependencies = workspace.act(new FilesResolverCallable(
                        new JenkinsBuildInfoLog(listener), username, password, resolverServer.getUrl(), spec, proxyConfiguration));
            } else {
                dependenciesClient = resolverServer.createArtifactoryDependenciesClient(username, password, proxyConfiguration, listener);
                GenericArtifactsResolver artifactsResolver = new GenericArtifactsResolver(build, listener, dependenciesClient);
                publishedDependencies = artifactsResolver.retrievePublishedDependencies(resolvePattern);
                buildDependencies = artifactsResolver.retrieveBuildDependencies(resolvePattern);
            }

            return createEnvironmentOnSuccessfulSetup();
        } catch (Exception e) {
            e.printStackTrace(listener.error(e.getMessage()));
            build.setResult(Result.FAILURE);
        } finally {
            if (dependenciesClient != null) {
                dependenciesClient.close();
            }
        }
        return null;
    }

    private Environment createEnvironmentOnSuccessfulSetup() {
        return new Environment() {
            @Override
            public boolean tearDown(AbstractBuild build, BuildListener listener)
                    throws IOException, InterruptedException {
                Result result = build.getResult();
                if (result != null && result.isWorseThan(Result.SUCCESS)) {
                    return true;    // build failed. Don't publish
                }

                ArtifactoryServer server = getArtifactoryServer();
                CredentialsConfig preferredDeployer = CredentialManager.getPreferredDeployer(ArtifactoryGenericConfigurator.this, server);
                ArtifactoryBuildInfoClient client = server.createArtifactoryClient(preferredDeployer.provideUsername(build.getProject()),
                        preferredDeployer.providePassword(build.getProject()), server.createProxyConfiguration(Jenkins.getInstance().proxy));
                server.setLog(listener, client);
                try {
                    boolean isFiltered = false;
                    if (isMultiConfProject(build)) {
                        if (multiConfProject && StringUtils.isBlank(getArtifactoryCombinationFilter())) {
                            String error = "The field \"Combination Matches\" is empty, but is defined as mandatory!";
                            listener.getLogger().println(error);
                            build.setResult(Result.FAILURE);
                            throw new IllegalArgumentException(error);
                        }
                        isFiltered = MultiConfigurationUtils.isfiltrated(build, getArtifactoryCombinationFilter());
                    }

                    if (!isFiltered) {
                        GenericArtifactsDeployer artifactsDeployer = new GenericArtifactsDeployer(build,
                                ArtifactoryGenericConfigurator.this, listener, preferredDeployer);
                        artifactsDeployer.deploy();

                        List<Artifact> deployedArtifacts = artifactsDeployer.getDeployedArtifacts();
                        if (deployBuildInfo) {
                            new GenericBuildInfoDeployer(ArtifactoryGenericConfigurator.this, client, build,
                                    listener, deployedArtifacts, buildDependencies, publishedDependencies).deploy();
                            String buildName = BuildUniqueIdentifierHelper.getBuildNameConsiderOverride(ArtifactoryGenericConfigurator.this, build);
                            // add the result action (prefer always the same index)
                            build.getActions().add(0, new BuildInfoResultAction(getArtifactoryUrl(), build, buildName));
                            build.getActions().add(new UnifiedPromoteBuildAction(build, ArtifactoryGenericConfigurator.this));
                            // Checks if Push to Bintray is disabled.
                            if (PluginsUtils.isPushToBintrayEnabled()) {
                                build.getActions().add(new BintrayPublishAction<ArtifactoryGenericConfigurator>(build,
                                        ArtifactoryGenericConfigurator.this));
                            }

                        }
                    }

                    return true;
                } catch (Exception e) {
                    e.printStackTrace(listener.error(e.getMessage()));
                } finally {
                    client.close();
                }

                // failed
                build.setResult(Result.FAILURE);
                return true;
            }
        };
    }

    private boolean isMultiConfProject(AbstractBuild build) {
        return (build.getProject().getClass().equals(MatrixConfiguration.class));
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    public boolean isOverridingDefaultResolver() {
        return resolverCredentialsConfig != null && resolverCredentialsConfig.isCredentialsProvided();
    }

    public Credentials getOverridingResolverCredentials() {
        return overridingResolverCredentials;
    }


    public CredentialsConfig getResolverCredentialsConfig() {
        return resolverCredentialsConfig;
    }

    @Extension(optional = true)
    public static class DescriptorImpl extends BuildWrapperDescriptor {
        private List<Repository> releaseRepositories;
        private AbstractProject<?, ?> item;

        public DescriptorImpl() {
            super(ArtifactoryGenericConfigurator.class);
            load();
        }

        @Override
        public boolean isApplicable(AbstractProject<?, ?> item) {
            this.item = item;
            return item.getClass().isAssignableFrom(FreeStyleProject.class) ||
                    item.getClass().isAssignableFrom(MatrixProject.class) ||
                    (Jenkins.getInstance().getPlugin(PluginsUtils.MULTIJOB_PLUGIN_ID) != null &&
                            item.getClass().isAssignableFrom(MultiJobProject.class));
        }

        /**
         * This method triggered from the client side by Ajax call.
         * The Element that trig is the "Refresh Repositories" button.
         *
         * @param url                 Artifactory url
         * @param credentialsId       credentials Id if using Credentials plugin
         * @param username            credentials legacy mode username
         * @param password            credentials legacy mode password
         * @param overrideCredentials credentials legacy mode overridden
         * @return {@link org.jfrog.hudson.util.RefreshServerResponse} object that represents the response of the repositories
         */
        @JavaScriptMethod
        public RefreshServerResponse refreshFromArtifactory(String url, String credentialsId, String username, String password, boolean overrideCredentials) {
            RefreshServerResponse response = new RefreshServerResponse();
            CredentialsConfig credentialsConfig = new CredentialsConfig(username, password, credentialsId, overrideCredentials);
            ArtifactoryServer artifactoryServer = RepositoriesUtils.getArtifactoryServer(
                    url, RepositoriesUtils.getArtifactoryServers()
            );

            try {
                List<String> releaseRepositoryKeysFirst = RepositoriesUtils.getLocalRepositories(url, credentialsConfig,
                        artifactoryServer, item);

                Collections.sort(releaseRepositoryKeysFirst);
                releaseRepositories = RepositoriesUtils.createRepositoriesList(releaseRepositoryKeysFirst);
                response.setRepositories(releaseRepositories);
                response.setSuccess(true);

            } catch (Exception e) {
                response.setResponseMessage(e.getMessage());
                response.setSuccess(false);
            }

            return response;
        }

        @SuppressWarnings("unused")
        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item project) {
            return PluginsUtils.fillPluginCredentials(project);
        }

        @Override
        public String getDisplayName() {
            return "Generic-Artifactory Integration";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            req.bindParameters(this, "generic");
            save();
            return true;
        }

        public boolean isMultiConfProject() {
            return (item.getClass().isAssignableFrom(MatrixProject.class));
        }

        public FormValidation doCheckArtifactoryCombinationFilter(@QueryParameter String value)
                throws IOException, InterruptedException {
            return FormValidations.validateArtifactoryCombinationFilter(value);
        }

        /**
         * Returns the list of {@link org.jfrog.hudson.ArtifactoryServer} configured.
         *
         * @return can be empty but never null.
         */
        public List<ArtifactoryServer> getArtifactoryServers() {
            return RepositoriesUtils.getArtifactoryServers();
        }

        public boolean isUseCredentialsPlugin() {
            return PluginsUtils.isUseCredentialsPlugin();
        }
    }

    /**
     * Page Converter
     */
    public static final class ConverterImpl extends DeployerResolverOverriderConverter {
        public ConverterImpl(XStream2 xstream) {
            super(xstream);
        }
    }
}
