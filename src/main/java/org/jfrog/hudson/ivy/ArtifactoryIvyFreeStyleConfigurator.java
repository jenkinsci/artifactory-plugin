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

package org.jfrog.hudson.ivy;

import com.google.common.collect.Iterables;
import com.tikal.jenkins.plugins.multijob.MultiJobProject;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixProject;
import hudson.model.*;
import hudson.remoting.Which;
import hudson.tasks.Ant;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.FormValidation;
import hudson.util.XStream2;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jfrog.build.extractor.listener.ArtifactoryBuildListener;
import org.jfrog.hudson.*;
import org.jfrog.hudson.action.ActionableHelper;
import org.jfrog.hudson.release.UnifiedPromoteBuildAction;
import org.jfrog.hudson.util.*;
import org.jfrog.hudson.util.plugins.PluginsUtils;
import org.jfrog.hudson.util.publisher.PublisherContext;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.bind.JavaScriptMethod;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.jfrog.hudson.util.plugins.MultiConfigurationUtils.isfiltrated;
import static org.jfrog.hudson.util.plugins.MultiConfigurationUtils.validateCombinationFilter;


/**
 * Gradle-Artifactory plugin configuration, allows to add the server details, deployment username/password, as well as
 * flags to deploy ivy, maven, and artifacts, as well as specifications of the location of the remote plugin (.gradle)
 * groovy script.
 *
 * @author Tomer Cohen
 */
public class ArtifactoryIvyFreeStyleConfigurator extends BuildWrapper implements DeployerOverrider,
        BuildInfoAwareConfigurator, MultiConfigurationAware {

    public final String remotePluginLocation;
    public final boolean deployBuildInfo;
    public final boolean includeEnvVars;
    private final Credentials overridingDeployerCredentials;
    private final boolean runChecks;
    private final String violationRecipients;
    private final boolean includePublishArtifacts;
    private final String scopes;
    private final boolean licenseAutoDiscovery;
    private final boolean disableLicenseAutoDiscovery;
    private final String ivyPattern;
    private final String artifactPattern;
    private final boolean notM2Compatible;
    private final IncludesExcludes artifactDeploymentPatterns;
    private final boolean discardOldBuilds;
    private final boolean passIdentifiedDownstream;
    private final boolean discardBuildArtifacts;
    private final String matrixParams;
    private final boolean enableIssueTrackerIntegration;
    private final boolean filterExcludedArtifactsFromBuild;
    private ServerDetails details;
    private boolean deployArtifacts;
    private IncludesExcludes envVarsPatterns;
    private String aggregationBuildStatus;
    private boolean aggregateBuildIssues;
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
    /**
     * @deprecated: Use org.jfrog.hudson.DeployerOverrider#getOverridingDeployerCredentials()
     */
    @Deprecated
    private transient String scrambledPassword;

    @DataBoundConstructor
    public ArtifactoryIvyFreeStyleConfigurator(ServerDetails details, Credentials overridingDeployerCredentials,
                                               boolean deployArtifacts, String remotePluginLocation,
                                               boolean includeEnvVars, IncludesExcludes envVarsPatterns,
                                               boolean deployBuildInfo, boolean runChecks, String violationRecipients,
                                               boolean includePublishArtifacts, String scopes, boolean disableLicenseAutoDiscovery, String ivyPattern,
                                               String artifactPattern, boolean notM2Compatible, IncludesExcludes artifactDeploymentPatterns,
                                               boolean discardOldBuilds, boolean passIdentifiedDownstream, boolean discardBuildArtifacts,
                                               String matrixParams, boolean enableIssueTrackerIntegration, boolean aggregateBuildIssues,
                                               String aggregationBuildStatus, boolean blackDuckRunChecks, String blackDuckAppName,
                                               String blackDuckAppVersion, String blackDuckReportRecipients, String blackDuckScopes,
                                               boolean blackDuckIncludePublishedArtifacts, boolean autoCreateMissingComponentRequests,
                                               boolean autoDiscardStaleComponentRequests, boolean filterExcludedArtifactsFromBuild,
                                               String artifactoryCombinationFilter) {
        this.details = details;
        this.overridingDeployerCredentials = overridingDeployerCredentials;
        this.deployArtifacts = deployArtifacts;
        this.remotePluginLocation = remotePluginLocation;
        this.includeEnvVars = includeEnvVars;
        this.envVarsPatterns = envVarsPatterns;
        this.deployBuildInfo = deployBuildInfo;
        this.runChecks = runChecks;
        this.violationRecipients = violationRecipients;
        this.includePublishArtifacts = includePublishArtifacts;
        this.scopes = scopes;
        this.disableLicenseAutoDiscovery = disableLicenseAutoDiscovery;
        this.ivyPattern = ivyPattern;
        this.aggregationBuildStatus = aggregationBuildStatus;
        this.filterExcludedArtifactsFromBuild = filterExcludedArtifactsFromBuild;
        this.artifactPattern = clearApostrophes(artifactPattern);
        this.notM2Compatible = notM2Compatible;
        this.artifactDeploymentPatterns = artifactDeploymentPatterns;
        this.discardOldBuilds = discardOldBuilds;
        this.passIdentifiedDownstream = passIdentifiedDownstream;
        this.matrixParams = matrixParams;
        this.enableIssueTrackerIntegration = enableIssueTrackerIntegration;
        this.aggregateBuildIssues = aggregateBuildIssues;
        this.licenseAutoDiscovery = !disableLicenseAutoDiscovery;
        this.discardBuildArtifacts = discardBuildArtifacts;
        this.blackDuckRunChecks = blackDuckRunChecks;
        this.blackDuckAppName = blackDuckAppName;
        this.blackDuckAppVersion = blackDuckAppVersion;
        this.blackDuckReportRecipients = blackDuckReportRecipients;
        this.blackDuckScopes = blackDuckScopes;
        this.blackDuckIncludePublishedArtifacts = blackDuckIncludePublishedArtifacts;
        this.autoCreateMissingComponentRequests = autoCreateMissingComponentRequests;
        this.autoDiscardStaleComponentRequests = autoDiscardStaleComponentRequests;
        this.artifactoryCombinationFilter = artifactoryCombinationFilter;
    }

    /**
     * Clears the extra apostrophes from the start and the end of the string
     */
    private String clearApostrophes(String artifactPattern) {
        return StringUtils.removeEnd(StringUtils.removeStart(artifactPattern, "\""), "\"");
    }

    public ServerDetails getDetails() {
        return details;
    }

    public String getMatrixParams() {
        return matrixParams;
    }

    public boolean isPassIdentifiedDownstream() {
        return passIdentifiedDownstream;
    }

    public boolean isDiscardBuildArtifacts() {
        return discardBuildArtifacts;
    }

    public boolean isDiscardOldBuilds() {
        return discardOldBuilds;
    }

    public boolean isOverridingDefaultDeployer() {
        return (getOverridingDeployerCredentials() != null);
    }

    public Credentials getOverridingDeployerCredentials() {
        return overridingDeployerCredentials;
    }

    public String getViolationRecipients() {
        return violationRecipients;
    }

    public String getArtifactPattern() {
        return clearApostrophes(artifactPattern);
    }

    public String getIvyPattern() {
        return ivyPattern;
    }

    public IncludesExcludes getArtifactDeploymentPatterns() {
        return artifactDeploymentPatterns;
    }

    public boolean isRunChecks() {
        return runChecks;
    }

    public boolean isIncludePublishArtifacts() {
        return includePublishArtifacts;
    }

    public boolean isDeployBuildInfo() {
        return deployBuildInfo;
    }

    public boolean isLicenseAutoDiscovery() {
        return licenseAutoDiscovery;
    }

    public boolean isDisableLicenseAutoDiscovery() {
        return disableLicenseAutoDiscovery;
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

    public String getRepositoryKey() {
        return details != null ? details.getDeployReleaseRepository().getRepoKey() : null;
    }

    public String getArtifactoryName() {
        return details != null ? details.artifactoryName : null;
    }

    public String getArtifactoryUrl() {
        return details != null ? details.getArtifactoryUrl() : null;
    }

    public boolean isDeployArtifacts() {
        return deployArtifacts;
    }

    public boolean isNotM2Compatible() {
        return notM2Compatible;
    }

    public boolean isM2Compatible() {
        return !notM2Compatible;
    }

    public boolean isEnableIssueTrackerIntegration() {
        return enableIssueTrackerIntegration;
    }

    public boolean isAggregateBuildIssues() {
        return aggregateBuildIssues;
    }

    public String getAggregationBuildStatus() {
        return aggregationBuildStatus;
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

    public boolean isFilterExcludedArtifactsFromBuild() {
        return filterExcludedArtifactsFromBuild;
    }

    public String getArtifactoryCombinationFilter() {
        return artifactoryCombinationFilter;
    }

    public boolean isMultiConfProject() {
        return getDescriptor().isMultiConfProject();
    }

    @Override
    public Collection<? extends Action> getProjectActions(AbstractProject project) {
        return ActionableHelper.getArtifactoryProjectAction(getArtifactoryUrl(), project);
    }

    @Override
    public Environment setUp(final AbstractBuild build, Launcher launcher, final BuildListener listener)
            throws IOException, InterruptedException {
        PublisherContext.Builder publisherBuilder = getBuilder();
        RepositoriesUtils.validateServerConfig(build, listener, getArtifactoryServer(), getArtifactoryUrl());
        final String buildName = BuildUniqueIdentifierHelper.getBuildName(build);
        int totalBuilds = 1;

        if (isMultiConfProject(build) && isDeployArtifacts()) {
            validateCombinationFilter(build, listener, getArtifactoryCombinationFilter());
            boolean isFiltered = isfiltrated(build, getArtifactoryCombinationFilter());
            if (isFiltered) {
                publisherBuilder.skipBuildInfoDeploy(true).deployArtifacts(false);
            }
            totalBuilds = ((MatrixProject) build.getParent().getParent()).getActiveConfigurations().size();
        }

        final Ant antBuild = getLastAntBuild(build.getProject());

        if (antBuild != null) {
            // The ConcurrentBuildSetupSync helper class is used to make sure that the code
            // inside its setUp() method is invoked by only one job in this build
            // (matrix project builds include more that one job) and that all other jobs
            // wait till the seUup() method finishes.
            new ConcurrentJobsHelper.ConcurrentBuildSetupSync(buildName, totalBuilds) {
                @Override
                public void setUp() {
                    // Obtain the current build and use it to store the configured targets.
                    // We store them because we override them during the build and we'll need
                    // their original values at the tear down stage so that they can be restored.
                    ConcurrentJobsHelper.ConcurrentBuild concurrentBuild = ConcurrentJobsHelper.concurrentBuildHandler.get(buildName);
                    concurrentBuild.putParam("targets", antBuild.getTargets());
                    // Override the targets after we stored them:
                    setTargetsField(antBuild, antBuild.getTargets() + getAntArgs());
                }
            };
        }

        build.setResult(Result.SUCCESS);
        final PublisherContext finalPublisherContext = publisherBuilder.build();

        return new Environment() {
            @Override
            public void buildEnvVars(Map<String, String> env) {
                super.buildEnvVars(env);
                try {
                    String actualDependencyDirPath = actualDependencyDirPath(build);
                    env.put("ARTIFACTORY_CACHE_LIBS", actualDependencyDirPath);
                    ExtractorUtils.addBuilderInfoArguments(env, build, listener, finalPublisherContext, null);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public boolean tearDown(AbstractBuild build, BuildListener listener)
                    throws IOException, InterruptedException {
                Result result = build.getResult();

                if (antBuild != null) {
                    // The ConcurrentBuildTearDownSync helper class is used to make sure that the code
                    // inside its tearDown() method is invoked by only one job in this build
                    // (matrix project builds include more that one job) and that this
                    // job is the last one running.
                    new ConcurrentJobsHelper.ConcurrentBuildTearDownSync(buildName, result) {
                        @Override
                        public void tearDown() {
                            // Restore the original targets of this build (we overrided their
                            // values in the setUp stage):
                            ConcurrentJobsHelper.ConcurrentBuild concurrentBuild = ConcurrentJobsHelper.concurrentBuildHandler.get(buildName);
                            String targets = concurrentBuild.getParam("targets");
                            setTargetsField(antBuild, targets);
                        }
                    };
                }

                if (!finalPublisherContext.isSkipBuildInfoDeploy() && (result == null ||
                        result.isBetterOrEqualTo(Result.SUCCESS))) {
                    build.getActions().add(0, new BuildInfoResultAction(getArtifactoryUrl(), build));
                    build.getActions().add(new UnifiedPromoteBuildAction<ArtifactoryIvyFreeStyleConfigurator>(build,
                            ArtifactoryIvyFreeStyleConfigurator.this));
                }

                // Aborted action by the user:
                if (Result.ABORTED.equals(result)) {
                    ConcurrentJobsHelper.concurrentBuildHandler.remove(buildName);
                }
listener.getLogger().println("********** " + ConcurrentJobsHelper.concurrentBuildHandler.size());
                return true;
            }
        };
    }

    private String actualDependencyDirPath(AbstractBuild build) throws IOException, InterruptedException {
        File localDependencyFile = Which.jarFile(ArtifactoryBuildListener.class);
        FilePath actualDependencyDir =
                PluginDependencyHelper.getActualDependencyDirectory(build, localDependencyFile);
        String actualDependencyDirPath = actualDependencyDir.getRemote();
        actualDependencyDirPath = actualDependencyDirPath.replace('\\', '/');
        actualDependencyDirPath = "\"" + actualDependencyDirPath + "\"";
        return actualDependencyDirPath;
    }

    private void setTargetsField(Ant builder, String targets) {
        try {
            Field targetsField = builder.getClass().getDeclaredField("targets");
            targetsField.setAccessible(true);
            targetsField.set(builder, targets);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String getAntArgs() {
        String lib = " -lib ${ARTIFACTORY_CACHE_LIBS} ";
        String listener = "-listener org.jfrog.build.extractor.listener.ArtifactoryBuildListener";

        return lib + listener;
    }

    private Ant getLastAntBuild(AbstractProject project) {
        if (project instanceof Project) {
            List<Ant> ants = ActionableHelper.getBuilder((Project) project, Ant.class);
            return Iterables.getLast(ants, null);
        }
        return null;
    }

    private PublisherContext.Builder getBuilder() {
        return new PublisherContext.Builder().
                artifactoryServer(getArtifactoryServer())
                .serverDetails(getDetails()).deployerOverrider(ArtifactoryIvyFreeStyleConfigurator.this)
                .runChecks(isRunChecks()).includePublishArtifacts(isIncludePublishArtifacts())
                .violationRecipients(getViolationRecipients()).scopes(getScopes())
                .licenseAutoDiscovery(isLicenseAutoDiscovery()).discardOldBuilds(isDiscardOldBuilds())
                .deployArtifacts(isDeployArtifacts()).includesExcludes(getArtifactDeploymentPatterns())
                .skipBuildInfoDeploy(!isDeployBuildInfo())
                .includeEnvVars(isIncludeEnvVars()).envVarsPatterns(getEnvVarsPatterns())
                .discardBuildArtifacts(isDiscardBuildArtifacts()).matrixParams(getMatrixParams())
                .maven2Compatible(isM2Compatible()).artifactsPattern(getArtifactPattern())
                .ivyPattern(getIvyPattern()).enableIssueTrackerIntegration(isEnableIssueTrackerIntegration())
                .aggregateBuildIssues(isAggregateBuildIssues()).aggregationBuildStatus(getAggregationBuildStatus())
                .integrateBlackDuck(isBlackDuckRunChecks(), getBlackDuckAppName(), getBlackDuckAppVersion(),
                        getBlackDuckReportRecipients(), getBlackDuckScopes(), isBlackDuckIncludePublishedArtifacts(),
                        isAutoCreateMissingComponentRequests(), isAutoDiscardStaleComponentRequests())
                .filterExcludedArtifactsFromBuild(isFilterExcludedArtifactsFromBuild());
    }

    public ArtifactoryServer getArtifactoryServer() {
        return RepositoriesUtils.getArtifactoryServer(getArtifactoryName(), getDescriptor().getArtifactoryServers());
    }

    public List<Repository> getReleaseRepositoryList() {
        return RepositoriesUtils.collectRepositories(getDescriptor().releaseRepositoryList,
                details.getDeploySnapshotRepository().getKeyFromSelect());
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
        private AbstractProject<?, ?> item;

        public DescriptorImpl() {
            super(ArtifactoryIvyFreeStyleConfigurator.class);
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
         * @param url                           the artifactory url
         * @param credentialsUsername           override credentials user name
         * @param credentialsPassword           override credentials password
         * @param overridingDeployerCredentials user choose to override credentials
         * @return {@link org.jfrog.hudson.util.RefreshServerResponse} object that represents the response of the repositories
         */
        @JavaScriptMethod
        public RefreshServerResponse refreshFromArtifactory(String url, String credentialsUsername,
                                                            String credentialsPassword,
                                                            boolean overridingDeployerCredentials) {
            RefreshServerResponse response = new RefreshServerResponse();
            ArtifactoryServer artifactoryServer = RepositoriesUtils.getArtifactoryServer(url,
                    RepositoriesUtils.getArtifactoryServers());

            try {
                List<String> releaseRepositoryKeysFirst = RepositoriesUtils.getLocalRepositories(url,
                        credentialsUsername, credentialsPassword,
                        overridingDeployerCredentials, artifactoryServer);
                Collections.sort(releaseRepositoryKeysFirst);
                releaseRepositoryList = RepositoriesUtils.createRepositoriesList(releaseRepositoryKeysFirst);
                response.setRepositories(releaseRepositoryList);
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

        @Override
        public String getDisplayName() {
            return "Ant/Ivy-Artifactory Integration";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            req.bindParameters(this, "ivy");
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
