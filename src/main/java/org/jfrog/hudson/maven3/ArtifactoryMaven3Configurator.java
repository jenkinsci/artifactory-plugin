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

package org.jfrog.hudson.maven3;

import com.tikal.jenkins.plugins.multijob.MultiJobProject;
import hudson.Extension;
import hudson.Launcher;
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixProject;
import hudson.model.*;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.FormValidation;
import hudson.util.XStream2;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jfrog.hudson.*;
import org.jfrog.hudson.BintrayPublish.BintrayPublishAction;
import org.jfrog.hudson.action.ActionableHelper;
import org.jfrog.hudson.release.UnifiedPromoteBuildAction;
import org.jfrog.hudson.util.*;
import org.jfrog.hudson.util.plugins.MultiConfigurationUtils;
import org.jfrog.hudson.util.plugins.PluginsUtils;
import org.jfrog.hudson.util.publisher.PublisherContext;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.bind.JavaScriptMethod;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Freestyle Maven 3 configurator. Currently for publishing only.
 *
 * @author Noam Y. Tenne
 */
public class ArtifactoryMaven3Configurator extends BuildWrapper implements DeployerOverrider, ResolverOverrider,
        BuildInfoAwareConfigurator, MultiConfigurationAware {

    /**
     * Repository URL and repository to deploy artifacts to
     */
    private final ServerDetails details;
    private final ServerDetails resolverDetails;
    private final Credentials overridingDeployerCredentials;
    private final Credentials overridingResolverCredentials;
    /**
     * If checked (default) deploy maven artifacts
     */
    private final boolean deployArtifacts;
    private final IncludesExcludes artifactDeploymentPatterns;

    /**
     * Include environment variables in the generated build info
     */
    private final boolean includeEnvVars;

    private final boolean deployBuildInfo;
    private final boolean runChecks;
    private final String violationRecipients;
    private final boolean includePublishArtifacts;
    private final String scopes;
    private final boolean discardOldBuilds;
    private final boolean discardBuildArtifacts;
    private final String matrixParams;
    private final boolean enableIssueTrackerIntegration;
    private final boolean filterExcludedArtifactsFromBuild;
    private final boolean enableResolveArtifacts;
    private IncludesExcludes envVarsPatterns;
    private boolean licenseAutoDiscovery;
    private boolean disableLicenseAutoDiscovery;
    private boolean aggregateBuildIssues;
    private String aggregationBuildStatus;
    private boolean recordAllDependencies;
    private boolean blackDuckRunChecks;
    private String blackDuckAppName;
    private String blackDuckAppVersion;
    private String blackDuckReportRecipients; //csv
    private String blackDuckScopes; //csv
    private boolean blackDuckIncludePublishedArtifacts;
    private boolean autoCreateMissingComponentRequests;
    private boolean autoDiscardStaleComponentRequests;
    private String artifactoryCombinationFilter;
    /**
     * @deprecated: Use org.jfrog.hudson.DeployerOverrider#getOverridingDeployerCredentials()
     */
    @Deprecated
    private transient String username;

    // NOTE: The following getters are used by jelly. Do not remove them
    /**
     * @deprecated: Use org.jfrog.hudson.DeployerOverrider#getOverridingDeployerCredentials()
     */
    @Deprecated
    private transient String scrambledPassword;
    /**
     * @deprecated: Use org.jfrog.hudson.maven3.ArtifactoryMaven3Configurator#deployBuildInfo
     */
    @Deprecated
    private transient boolean skipBuildInfoDeploy;

    @DataBoundConstructor
    public ArtifactoryMaven3Configurator(ServerDetails details, ServerDetails resolverDetails,
                                         Credentials overridingDeployerCredentials,
                                         Credentials overridingResolverCredentials, boolean enableResolveArtifacts,
                                         IncludesExcludes artifactDeploymentPatterns, boolean deployArtifacts,
                                         boolean deployBuildInfo, boolean includeEnvVars,
                                         IncludesExcludes envVarsPatterns,
                                         boolean runChecks, String violationRecipients, boolean includePublishArtifacts,
                                         String scopes, boolean disableLicenseAutoDiscovery, boolean discardOldBuilds,
                                         boolean discardBuildArtifacts, String matrixParams,
                                         boolean enableIssueTrackerIntegration, boolean aggregateBuildIssues,
                                         String aggregationBuildStatus, boolean recordAllDependencies,
                                         boolean blackDuckRunChecks, String blackDuckAppName,
                                         String blackDuckAppVersion,
                                         String blackDuckReportRecipients, String blackDuckScopes,
                                         boolean blackDuckIncludePublishedArtifacts,
                                         boolean autoCreateMissingComponentRequests,
                                         boolean autoDiscardStaleComponentRequests,
                                         boolean filterExcludedArtifactsFromBuild,
                                         String artifactoryCombinationFilter
    ) {
        this.details = details;
        this.resolverDetails = resolverDetails;
        this.overridingDeployerCredentials = overridingDeployerCredentials;
        this.overridingResolverCredentials = overridingResolverCredentials;
        this.artifactDeploymentPatterns = artifactDeploymentPatterns;
        this.envVarsPatterns = envVarsPatterns;
        this.runChecks = runChecks;
        this.violationRecipients = violationRecipients;
        this.includePublishArtifacts = includePublishArtifacts;
        this.scopes = scopes;
        this.discardOldBuilds = discardOldBuilds;
        this.discardBuildArtifacts = discardBuildArtifacts;
        this.matrixParams = matrixParams;
        this.enableIssueTrackerIntegration = enableIssueTrackerIntegration;
        this.aggregateBuildIssues = aggregateBuildIssues;
        this.aggregationBuildStatus = aggregationBuildStatus;
        this.recordAllDependencies = recordAllDependencies;
        this.filterExcludedArtifactsFromBuild = filterExcludedArtifactsFromBuild;
        this.licenseAutoDiscovery = !disableLicenseAutoDiscovery;
        this.deployBuildInfo = deployBuildInfo;
        this.deployArtifacts = deployArtifacts;
        this.includeEnvVars = includeEnvVars;
        this.blackDuckRunChecks = blackDuckRunChecks;
        this.blackDuckAppName = blackDuckAppName;
        this.blackDuckAppVersion = blackDuckAppVersion;
        this.blackDuckReportRecipients = blackDuckReportRecipients;
        this.blackDuckScopes = blackDuckScopes;
        this.blackDuckIncludePublishedArtifacts = blackDuckIncludePublishedArtifacts;
        this.autoCreateMissingComponentRequests = autoCreateMissingComponentRequests;
        this.autoDiscardStaleComponentRequests = autoDiscardStaleComponentRequests;
        this.artifactoryCombinationFilter = artifactoryCombinationFilter;
        this.enableResolveArtifacts = enableResolveArtifacts;
    }

    public ServerDetails getDetails() {
        return details;
    }

    public ServerDetails getResolverDetails() {
        return resolverDetails;
    }

    public String getDownloadReleaseRepositoryKey() {
        return resolverDetails != null ? resolverDetails.getResolveReleaseRepositoryKey() : null;
    }

    public String getDownloadSnapshotRepositoryKey() {
        return resolverDetails != null ? resolverDetails.getResolveReleaseRepositoryKey() : null;
    }

    public boolean isDiscardOldBuilds() {
        return discardOldBuilds;
    }

    public boolean isDiscardBuildArtifacts() {
        return discardBuildArtifacts;
    }

    public boolean isOverridingDefaultDeployer() {
        return (getOverridingDeployerCredentials() != null);
    }

    public Credentials getOverridingDeployerCredentials() {
        return overridingDeployerCredentials;
    }

    public boolean isOverridingDefaultResolver() {
        return (getOverridingResolverCredentials() != null);
    }

    public Credentials getOverridingResolverCredentials() {
        return overridingResolverCredentials;
    }

    public boolean isOverridingResolverCredentials() {
        return (getOverridingResolverCredentials() != null);
    }

    public boolean isDeployArtifacts() {
        return deployArtifacts;
    }

    public String getMatrixParams() {
        return matrixParams;
    }

    public IncludesExcludes getArtifactDeploymentPatterns() {
        return artifactDeploymentPatterns;
    }

    public boolean isDeployBuildInfo() {
        return deployBuildInfo;
    }

    public boolean isRecordAllDependencies() {
        return recordAllDependencies;
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

    @SuppressWarnings({"UnusedDeclaration"})
    public String getRepositoryKey() {
        return details != null ? details.getDeployReleaseRepositoryKey() : null;
    }

    public boolean isIncludePublishArtifacts() {
        return includePublishArtifacts;
    }

    /**
     * @return The snapshots deployment repository. If not defined the releases deployment repository will be returned
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public String getSnapshotsRepositoryKey() {
        return details != null ?
                (details.getDeploySnapshotRepository() != null ? details.getDeploySnapshotRepository().getRepoKey() :
                        details.getDeployReleaseRepository().getRepoKey()) :
                null;
    }

    public boolean isLicenseAutoDiscovery() {
        return licenseAutoDiscovery;
    }

    public boolean isDisableLicenseAutoDiscovery() {
        return disableLicenseAutoDiscovery;
    }

    public String getArtifactoryName() {
        return details != null ? details.artifactoryName : null;
    }

    public String getArtifactoryUrl() {
        return details != null ? details.getArtifactoryUrl() : null;
    }

    public String getScopes() {
        return scopes;
    }

    public boolean isIncludeEnvVars() {
        return includeEnvVars;
    }

    public IncludesExcludes getEnvVarsPatterns() {
        return envVarsPatterns;
    }

    public String getViolationRecipients() {
        return violationRecipients;
    }

    public boolean isRunChecks() {
        return runChecks;
    }

    public boolean isBlackDuckRunChecks() {
        return blackDuckRunChecks;
    }

    public String getBlackDuckAppName() {
        return blackDuckAppName;
    }

    public String getBlackDuckAppVersion() {
        return blackDuckAppVersion;
    }

    public String getBlackDuckReportRecipients() {
        return blackDuckReportRecipients;
    }

    public String getBlackDuckScopes() {
        return blackDuckScopes;
    }

    public boolean isBlackDuckIncludePublishedArtifacts() {
        return blackDuckIncludePublishedArtifacts;
    }

    public boolean isAutoCreateMissingComponentRequests() {
        return autoCreateMissingComponentRequests;
    }

    public boolean isAutoDiscardStaleComponentRequests() {
        return autoDiscardStaleComponentRequests;
    }

    public boolean isEnableIssueTrackerIntegration() {
        return enableIssueTrackerIntegration;
    }

    public boolean isAggregateBuildIssues() {
        return aggregateBuildIssues;
    }

    public boolean isEnableResolveArtifacts() {
        return enableResolveArtifacts;
    }

    public String getAggregationBuildStatus() {
        return aggregationBuildStatus;
    }

    public boolean isFilterExcludedArtifactsFromBuild() {
        return filterExcludedArtifactsFromBuild;
    }

    public String getArtifactoryCombinationFilter() {
        return artifactoryCombinationFilter;
    }

    public boolean isMultiConfProject() {
        return getDescriptor().isMultiConfProject();
    }

    public ArtifactoryServer getArtifactoryServer(String artifactoryServerName) {
        return RepositoriesUtils.getArtifactoryServer(artifactoryServerName, getDescriptor().getArtifactoryServers());
    }

    public List<Repository> getReleaseRepositoryList() {
        return RepositoriesUtils.collectRepositories(getDescriptor().releaseRepositoryList,
                details.getDeployReleaseRepository().getKeyFromSelect());
    }

    public List<Repository> getSnapshotRepositoryList() {
        return RepositoriesUtils.collectRepositories(getDescriptor().snapshotRepositoryList,
                details.getDeploySnapshotRepository().getKeyFromSelect());
    }

    public List<VirtualRepository> getResolveReleaseRepositoryList() {
        return RepositoriesUtils.collectVirtualRepositories(getDescriptor().virtualRepositoryList,
                resolverDetails.getResolveReleaseRepository().getKeyFromSelect());
    }

    public List<VirtualRepository> getResolveSnapshotRepositoryList() {
        return RepositoriesUtils.collectVirtualRepositories(getDescriptor().virtualRepositoryList,
                resolverDetails.getResolveSnapshotRepository().getKeyFromSelect());
    }


    @Override
    public Collection<? extends Action> getProjectActions(AbstractProject project) {
        return ActionableHelper.getArtifactoryProjectAction(details.getArtifactoryUrl(), project);
    }

    @Override
    public Environment setUp(final AbstractBuild build, Launcher launcher, final BuildListener listener)
            throws IOException, InterruptedException {

        final String artifactoryServerName = getArtifactoryName();
        if (StringUtils.isBlank(artifactoryServerName)) {
            return super.setUp(build, launcher, listener);
        }
        final ArtifactoryServer artifactoryServer = getArtifactoryServer(artifactoryServerName);
        if (artifactoryServer == null) {
            listener.getLogger().format("No Artifactory server configured for %s. " +
                    "Please check your configuration.", artifactoryServerName).println();
            build.setResult(Result.FAILURE);
            throw new IllegalArgumentException("No Artifactory server configured for " + artifactoryServerName);
        }

        PublisherContext.Builder publisherBuilder = new PublisherContext.Builder().artifactoryServer(artifactoryServer)
                .serverDetails(getDetails()).deployerOverrider(ArtifactoryMaven3Configurator.this)
                .runChecks(isRunChecks()).includePublishArtifacts(isIncludePublishArtifacts())
                .violationRecipients(getViolationRecipients()).scopes(getScopes())
                .licenseAutoDiscovery(isLicenseAutoDiscovery()).discardOldBuilds(isDiscardOldBuilds())
                .deployArtifacts(isDeployArtifacts()).includesExcludes(getArtifactDeploymentPatterns())
                .skipBuildInfoDeploy(skipBuildInfoDeploy).recordAllDependencies(isRecordAllDependencies())
                .includeEnvVars(isIncludeEnvVars()).envVarsPatterns(getEnvVarsPatterns())
                .discardBuildArtifacts(isDiscardBuildArtifacts()).matrixParams(getMatrixParams())
                .enableIssueTrackerIntegration(isEnableIssueTrackerIntegration())
                .aggregateBuildIssues(isAggregateBuildIssues()).aggregationBuildStatus(getAggregationBuildStatus())
                .integrateBlackDuck(isBlackDuckRunChecks(), getBlackDuckAppName(), getBlackDuckAppVersion(),
                        getBlackDuckReportRecipients(), getBlackDuckScopes(), isBlackDuckIncludePublishedArtifacts(),
                        isAutoCreateMissingComponentRequests(), isAutoDiscardStaleComponentRequests())
                .filterExcludedArtifactsFromBuild(isFilterExcludedArtifactsFromBuild());

        if (isMultiConfProject(build) && isDeployArtifacts()) {
            if (StringUtils.isBlank(getArtifactoryCombinationFilter())) {
                String error = "The field \"Combination Matches\" is empty, but is defined as mandatory!";
                listener.getLogger().println(error);
                build.setResult(Result.FAILURE);
                throw new IllegalArgumentException(error);
            }
            boolean isFiltered = MultiConfigurationUtils.isfiltrated(build, getArtifactoryCombinationFilter());
            if (isFiltered) {
                publisherBuilder.skipBuildInfoDeploy(true).deployArtifacts(false);
            }
        }

        ResolverContext resolver = null;
        if (isEnableResolveArtifacts()) {
            resolver = new ResolverContext(getArtifactoryServer(), getResolverDetails(),
                    overridingResolverCredentials, ArtifactoryMaven3Configurator.this);
        }
        final ResolverContext resolverContext = resolver;
        final PublisherContext publisherContext = publisherBuilder.build();
        build.setResult(Result.SUCCESS);

        return new Environment() {
            @Override
            public void buildEnvVars(Map<String, String> env) {
                try {
                    ExtractorUtils.addBuilderInfoArguments(env, build, listener, publisherContext, resolverContext);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public boolean tearDown(AbstractBuild build, BuildListener listener) {
                Result result = build.getResult();
                if (deployBuildInfo && result != null && result.isBetterOrEqualTo(Result.SUCCESS)) {
                    build.getActions().add(new BuildInfoResultAction(getArtifactoryUrl(), build));
                    build.getActions().add(new UnifiedPromoteBuildAction<ArtifactoryMaven3Configurator>(build, ArtifactoryMaven3Configurator.this));
                    build.getActions().add(new BintrayPublishAction<ArtifactoryMaven3Configurator>(build, ArtifactoryMaven3Configurator.this));
                }
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

    @Extension(optional = true)
    public static class DescriptorImpl extends BuildWrapperDescriptor {
        private List<Repository> releaseRepositoryList;
        private List<Repository> snapshotRepositoryList;
        private List<VirtualRepository> virtualRepositoryList;
        private AbstractProject<?, ?> item;

        public DescriptorImpl() {
            super(ArtifactoryMaven3Configurator.class);
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

        private void refreshVirtualRepositories(ArtifactoryServer artifactoryServer, String credentialsUsername, String credentialsPassword, boolean overridingDeployerCredentials) throws IOException {
            virtualRepositoryList = RepositoriesUtils.getVirtualRepositoryKeys(artifactoryServer.getUrl(), credentialsUsername, credentialsPassword, overridingDeployerCredentials, artifactoryServer);
            Collections.sort(virtualRepositoryList);
        }

        /**
         * This method triggered from the client side by Ajax call.
         * The Element that trig is the "Refresh Repositories" button.
         *
         * @param url                           the artifactory url
         * @param credentialsUsername           override credentials user name
         * @param credentialsPassword           override credentials password
         * @param overridingDeployerCredentials user choose to override credentials
         * @return {@link org.jfrog.hudson.util.RefreshServerResponse} object that represents the response of the repositories
         */
        @JavaScriptMethod
        public RefreshServerResponse refreshFromArtifactory(String url, String credentialsUsername, String credentialsPassword, boolean overridingDeployerCredentials) {
            RefreshServerResponse response = new RefreshServerResponse();
            ArtifactoryServer artifactoryServer = RepositoriesUtils.getArtifactoryServer(url, getArtifactoryServers());

            try {
                List<String> releaseRepositoryKeysFirst = RepositoriesUtils.getLocalRepositories(url, credentialsUsername, credentialsPassword,
                        overridingDeployerCredentials, artifactoryServer);

                Collections.sort(releaseRepositoryKeysFirst);
                releaseRepositoryList = RepositoriesUtils.createRepositoriesList(releaseRepositoryKeysFirst);
                snapshotRepositoryList = releaseRepositoryList;
                response.setRepositories(snapshotRepositoryList);
                response.setSuccess(true);

                return response;
            } catch (Exception e) {
                e.printStackTrace();
                response.setResponseMessage(e.getMessage());
                response.setSuccess(false);
            }

            /*
            * In case of Exception, we write error in the Javascript scope!
            * */
            return response;
        }

        /**
         * This method is triggered from the client side by ajax call.
         * The method is triggered by the "Refresh Repositories" button.
         *
         * @param url                           The artifactory url
         * @param credentialsUsername           Override credentials user name
         * @param credentialsPassword           Override credentials password
         * @param overridingDeployerCredentials Indicates whether to override the credentials
         * @return {@link org.jfrog.hudson.util.RefreshServerResponse} object that represents the response
         */
        @JavaScriptMethod
        public RefreshServerResponse refreshResolversFromArtifactory(String url, String credentialsUsername, String credentialsPassword, boolean overridingDeployerCredentials) {
            RefreshServerResponse response = new RefreshServerResponse();
            ArtifactoryServer artifactoryServer = RepositoriesUtils.getArtifactoryServer(url, getArtifactoryServers());

            try {
                refreshVirtualRepositories(artifactoryServer, credentialsUsername, credentialsPassword, overridingDeployerCredentials);
                response.setVirtualRepositories(virtualRepositoryList);
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

        @Override
        public String getDisplayName() {
            return "Maven3-Artifactory Integration";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            req.bindParameters(this, "maven3");
            save();
            return true;
        }

        public boolean isMultiConfProject() {
            return (item.getClass().isAssignableFrom(MatrixProject.class));
        }

        public FormValidation doCheckViolationRecipients(@QueryParameter String value) {
            return FormValidations.validateEmails(value);
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

        public boolean isJiraPluginEnabled() {
            return (Jenkins.getInstance().getPlugin("jira") != null);
        }
    }

    /**
     * Convert any remaining local credential variables to a credentials object
     */
    public static final class ConverterImpl extends OverridingDeployerCredentialsConverter {
        public ConverterImpl(XStream2 xstream) {
            super(xstream);
        }
    }

}
