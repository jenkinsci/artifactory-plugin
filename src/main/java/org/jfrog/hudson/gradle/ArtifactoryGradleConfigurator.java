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

package org.jfrog.hudson.gradle;

import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.tikal.jenkins.plugins.multijob.MultiJobProject;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixProject;
import hudson.model.*;
import hudson.model.listeners.RunListener;
import hudson.plugins.gradle.Gradle;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.FormValidation;
import hudson.util.XStream2;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jfrog.gradle.plugin.artifactory.task.BuildInfoBaseTask;
import org.jfrog.hudson.*;
import org.jfrog.hudson.BintrayPublish.BintrayPublishAction;
import org.jfrog.hudson.action.ActionableHelper;
import org.jfrog.hudson.action.ArtifactoryProjectAction;
import org.jfrog.hudson.release.ReleaseAction;
import org.jfrog.hudson.release.UnifiedPromoteBuildAction;
import org.jfrog.hudson.release.gradle.BaseGradleReleaseAction;
import org.jfrog.hudson.release.gradle.GradleReleaseAction;
import org.jfrog.hudson.release.gradle.GradleReleaseApiAction;
import org.jfrog.hudson.release.gradle.GradleReleaseWrapper;
import org.jfrog.hudson.util.*;
import org.jfrog.hudson.util.plugins.MultiConfigurationUtils;
import org.jfrog.hudson.util.plugins.PluginsUtils;
import org.jfrog.hudson.util.publisher.PublisherContext;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.bind.JavaScriptMethod;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;

/**
 * Gradle-Artifactory plugin configuration, allows to add the server details, deployment username/password, as well as
 * flags to deploy ivy, maven, and artifacts, as well as specifications of the location of the remote plugin (.gradle)
 * groovy script.
 *
 * @author Tomer Cohen
 */
public class ArtifactoryGradleConfigurator extends BuildWrapper implements DeployerOverrider, ResolverOverrider,
        BuildInfoAwareConfigurator, MultiConfigurationAware {
    public final boolean deployMaven;
    public final boolean deployIvy;
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
    private final boolean enableIssueTrackerIntegration;
    private final boolean aggregateBuildIssues;
    private final String artifactPattern;
    private final boolean notM2Compatible;
    private final IncludesExcludes artifactDeploymentPatterns;
    private final boolean discardOldBuilds;
    private final boolean passIdentifiedDownstream;
    private final GradleReleaseWrapper releaseWrapper;
    private final boolean discardBuildArtifacts;
    private final String matrixParams;
    private final boolean skipInjectInitScript;
    private final boolean allowPromotionOfNonStagedBuilds;
    private final boolean allowBintrayPushOfNonStageBuilds;
    private final boolean blackDuckRunChecks;
    private final String blackDuckAppName;
    private final String blackDuckAppVersion;
    private final boolean filterExcludedArtifactsFromBuild;
    private ServerDetails details;
    private boolean deployArtifacts;
    private IncludesExcludes envVarsPatterns;
    private String aggregationBuildStatus;
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
    public ArtifactoryGradleConfigurator(ServerDetails details, Credentials overridingDeployerCredentials,
                                         boolean deployMaven, boolean deployIvy, boolean deployArtifacts, String remotePluginLocation,
                                         boolean includeEnvVars, IncludesExcludes envVarsPatterns,
                                         boolean deployBuildInfo, boolean runChecks, String violationRecipients,
                                         boolean includePublishArtifacts, String scopes, boolean disableLicenseAutoDiscovery, String ivyPattern,
                                         String artifactPattern, boolean notM2Compatible, IncludesExcludes artifactDeploymentPatterns,
                                         boolean discardOldBuilds, boolean passIdentifiedDownstream, GradleReleaseWrapper releaseWrapper,
                                         boolean discardBuildArtifacts, String matrixParams, boolean skipInjectInitScript,
                                         boolean enableIssueTrackerIntegration, boolean aggregateBuildIssues, String aggregationBuildStatus,
                                         boolean allowPromotionOfNonStagedBuilds, boolean allowBintrayPushOfNonStageBuilds,
                                         boolean blackDuckRunChecks, String blackDuckAppName,
                                         String blackDuckAppVersion, String blackDuckReportRecipients, String blackDuckScopes,
                                         boolean blackDuckIncludePublishedArtifacts, boolean autoCreateMissingComponentRequests,
                                         boolean autoDiscardStaleComponentRequests, boolean filterExcludedArtifactsFromBuild,
                                         String artifactoryCombinationFilter) {
        this.details = details;
        this.overridingDeployerCredentials = overridingDeployerCredentials;
        this.deployMaven = deployMaven;
        this.deployIvy = deployIvy;
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
        this.enableIssueTrackerIntegration = enableIssueTrackerIntegration;
        this.aggregateBuildIssues = aggregateBuildIssues;
        this.aggregationBuildStatus = aggregationBuildStatus;
        this.filterExcludedArtifactsFromBuild = filterExcludedArtifactsFromBuild;
        this.artifactPattern = cleanString(artifactPattern);
        this.notM2Compatible = notM2Compatible;
        this.artifactDeploymentPatterns = artifactDeploymentPatterns;
        this.discardOldBuilds = discardOldBuilds;
        this.passIdentifiedDownstream = passIdentifiedDownstream;
        this.releaseWrapper = releaseWrapper;
        this.discardBuildArtifacts = discardBuildArtifacts;
        this.matrixParams = matrixParams;
        this.skipInjectInitScript = skipInjectInitScript;
        this.licenseAutoDiscovery = !disableLicenseAutoDiscovery;
        this.allowPromotionOfNonStagedBuilds = allowPromotionOfNonStagedBuilds;
        this.blackDuckRunChecks = blackDuckRunChecks;
        this.allowBintrayPushOfNonStageBuilds = allowBintrayPushOfNonStageBuilds;
        this.blackDuckAppName = blackDuckAppName;
        this.blackDuckAppVersion = blackDuckAppVersion;
        this.blackDuckReportRecipients = blackDuckReportRecipients;
        this.blackDuckScopes = blackDuckScopes;
        this.blackDuckIncludePublishedArtifacts = blackDuckIncludePublishedArtifacts;
        this.autoCreateMissingComponentRequests = autoCreateMissingComponentRequests;
        this.autoDiscardStaleComponentRequests = autoDiscardStaleComponentRequests;
        this.artifactoryCombinationFilter = artifactoryCombinationFilter;
    }

    public GradleReleaseWrapper getReleaseWrapper() {
        return releaseWrapper;
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

    public boolean isSkipInjectInitScript() {
        return skipInjectInitScript;
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
        return cleanString(artifactPattern);
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

    public String getUserPluginKey() {
        return details != null ? details.getUserPluginKey() : null;
    }

    public String getDownloadReleaseRepositoryKey() {
        return details != null ? details.getResolveReleaseRepository().getRepoKey() : null;
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

    public boolean isDeployMaven() {
        return deployMaven;
    }

    public boolean isDeployIvy() {
        return deployIvy;
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

    public boolean isAllowPromotionOfNonStagedBuilds() {
        return allowPromotionOfNonStagedBuilds;
    }

    public boolean isAllowBintrayPushOfNonStageBuilds() {
        return allowBintrayPushOfNonStageBuilds;
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

    private String cleanString(String artifactPattern) {
        return StringUtils.removeEnd(StringUtils.removeStart(artifactPattern, "\""), "\"");
    }

    @Override
    public Action getProjectAction(AbstractProject job) {
        return super.getProjectAction(job);
    }

    @Override
    public Collection<? extends Action> getProjectActions(AbstractProject project) {
        List<ArtifactoryProjectAction> action =
                ActionableHelper.getArtifactoryProjectAction(details.getArtifactoryUrl(), project);
        if (getReleaseWrapper() != null) {
            List<Action> actions = new ArrayList<Action>();
            actions.addAll(action);
            actions.add(new GradleReleaseAction(project));
            actions.add(new GradleReleaseApiAction(project));
            return actions;
        }
        return action;
    }

    @Override
    public Environment setUp(final AbstractBuild build, Launcher launcher, final BuildListener listener)
            throws IOException, InterruptedException {
        PublisherContext.Builder publisherBuilder = getBuilder();
        RepositoriesUtils.validateServerConfig(build, listener, getArtifactoryServer(), getArtifactoryUrl());

        final String buildName = BuildUniqueIdentifierHelper.getBuildName(build);
        int totalBuilds = 1;

        if (isMultiConfProject(build)) {
            totalBuilds = ((MatrixProject) build.getParent().getParent()).getActiveConfigurations().size();
            if (isDeployArtifacts()) {
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
        }

        if (isRelease(build)) {
            releaseWrapper.setUp(build, launcher, listener);
        }

        final Gradle gradleBuild = getLastGradleBuild(build.getProject());
        if (gradleBuild != null) {
            // The ConcurrentBuildSetupSync helper class is used to make sure that the code
            // inside its setUp() method is invoked by only one job in this build
            // (matrix project builds include more that one job) and that all other jobs
            // wait till the seUup() method finishes.
            new ConcurrentJobsHelper.ConcurrentBuildSetupSync(buildName, totalBuilds) {
                @Override
                public void setUp() {
                    // Obtain the current build and use it to store the configured switches and tasks.
                    // We store them because we override them during the build and we'll need
                    // their original values at the tear down stage so that they can be restored.
                    ConcurrentJobsHelper.ConcurrentBuild concurrentBuild = ConcurrentJobsHelper.concurrentBuildHandler.get(buildName);

                    // Remove the Artifactory Plugin additional switches and tasks,
                    // in case they are included in the targets string:
                    String switches = gradleBuild.getSwitches() != null ? gradleBuild.getSwitches().replace("${ARTIFACTORY_INIT_SCRIPT}", "") : "";
                    String tasks = gradleBuild.getTasks() != null ? gradleBuild.getTasks().replace("${ARTIFACTORY_TASKS}", "") : "";

                    concurrentBuild.putParam("switches", switches);
                    concurrentBuild.putParam("tasks", tasks);

                    // Override the build switches:
                    if (!skipInjectInitScript) {
                        setTargetsField(gradleBuild, "switches", switches + " " + "${ARTIFACTORY_INIT_SCRIPT}");
                    }
                    // Override the build tasks:
                    if (!StringUtils.contains(gradleBuild.getTasks(), BuildInfoBaseTask.BUILD_INFO_TASK_NAME)) {
                        // In case we specified "alternative goals" in the release view we should override the build goals
                        if (isRelease(build) && StringUtils.isNotBlank(releaseWrapper.getAlternativeTasks())) {
                            tasks = "${ARTIFACTORY_TASKS}";
                        } else {
                            tasks += " ${ARTIFACTORY_TASKS}";
                        }
                        setTargetsField(gradleBuild, "tasks", tasks);
                    }
                }
            };
        } else {
            listener.getLogger().println("[Warning] No Gradle build configured");
        }

        final PublisherContext.Builder finalPublisherBuilder = publisherBuilder;

        return new Environment() {
            @Override
            public void buildEnvVars(Map<String, String> env) {
                GradleInitScriptWriter writer = new GradleInitScriptWriter(build);
                FilePath workspace = build.getWorkspace();
                FilePath initScript;
                String initScriptPath;
                try {
                    initScript =
                            workspace.createTextTempFile("init-artifactory", "gradle", writer.generateInitScript(),
                                    false);
                    initScriptPath = initScript.getRemote();
                    initScriptPath = initScriptPath.replace('\\', '/');
                    env.put("ARTIFACTORY_INIT_SCRIPT", " --init-script " + initScriptPath);

                } catch (Exception e) {
                    listener.getLogger().println("Error occurred while writing Gradle Init Script: " + e.getMessage());
                    build.setResult(Result.FAILURE);
                }

                String tasks = StringUtils.EMPTY;
                if (isRelease(build)) {
                    String alternativeGoals = releaseWrapper.getAlternativeTasks();
                    if (StringUtils.isNotBlank(alternativeGoals)) {
                        tasks = alternativeGoals;
                    }
                }
                env.put("ARTIFACTORY_TASKS", tasks + " " + BuildInfoBaseTask.BUILD_INFO_TASK_NAME);

                ServerDetails serverDetails = getDetails();
                ReleaseAction releaseAction = ActionableHelper.getLatestAction(build, ReleaseAction.class);
                if (releaseAction != null) {
                    String stagingRepository = releaseAction.getStagingRepositoryKey();
                    if (StringUtils.isBlank(stagingRepository)) {
                        stagingRepository = getRepositoryKey();
                    }
                    serverDetails = new ServerDetails(
                            serverDetails.artifactoryName,
                            serverDetails.getArtifactoryUrl(),
                            new RepositoryConf(stagingRepository, stagingRepository, false),
                            serverDetails.getDeploySnapshotRepository(),
                            serverDetails.getResolveReleaseRepository(),
                            serverDetails.getResolveSnapshotRepository());
                }
                finalPublisherBuilder.serverDetails(serverDetails);

                ResolverContext resolverContext = null;
                if (StringUtils.isNotBlank(serverDetails.getResolveReleaseRepository().getRepoKey())) {
                    // Resolution server and overriding credentials are currently shared by the deployer and resolver in
                    // the UI. So here we use the same server details and for credentials we try deployer override and
                    // then default resolver
                    Credentials resolverCredentials;
                    if (isOverridingDefaultDeployer()) {
                        resolverCredentials = getOverridingDeployerCredentials();
                    } else {
                        resolverCredentials = getArtifactoryServer().getResolvingCredentials();
                    }
                    resolverContext = new ResolverContext(getArtifactoryServer(), serverDetails, resolverCredentials,
                            ArtifactoryGradleConfigurator.this);
                }

                try {
                    ExtractorUtils.addBuilderInfoArguments(env, build, listener, finalPublisherBuilder.build(), resolverContext);
                } catch (Exception e) {
                    listener.getLogger().println(e.getMessage());
                    throw new RuntimeException(e);
                }
            }

            @Override
            public boolean tearDown(AbstractBuild build, BuildListener listener)
                    throws IOException, InterruptedException {
                boolean success = false;
                boolean releaseSuccess = true;
                Result result = build.getResult();

                if (isRelease(build)) {
                    releaseSuccess = releaseWrapper.tearDown(build, listener);
                }
                if (gradleBuild != null) {
                    // The ConcurrentBuildTearDownSync helper class is used to make sure that the code
                    // inside its tearDown() method is invoked by only one job in this build
                    // (matrix project builds include more that one job) and that this
                    // job is the last one running.
                    new ConcurrentJobsHelper.ConcurrentBuildTearDownSync(buildName, result) {
                        @Override
                        public void tearDown() {
                            // Restore the original switches and tasks of this build (we overrided their
                            // values in the setUp stage):
                            ConcurrentJobsHelper.ConcurrentBuild build = ConcurrentJobsHelper.concurrentBuildHandler.get(buildName);
                            String switches = build.getParam("switches");
                            String tasks = build.getParam("tasks");
                            switches = switches.replace("${ARTIFACTORY_INIT_SCRIPT}", "");
                            tasks = tasks.replace("${ARTIFACTORY_TASKS}", "");
                            setTargetsField(gradleBuild, "switches", switches);
                            setTargetsField(gradleBuild, "tasks", tasks);
                        }
                    };
                }
                if (result != null && result.isBetterOrEqualTo(Result.SUCCESS)) {
                    if (isDeployBuildInfo()) {
                        build.getActions().add(new BuildInfoResultAction(getArtifactoryUrl(), build));
                        ArtifactoryGradleConfigurator configurator = ActionableHelper.getBuildWrapper(
                                build.getProject(), ArtifactoryGradleConfigurator.class);
                        if (configurator != null) {
                            if (isAllowPromotionOfNonStagedBuilds()) {
                                build.getActions()
                                        .add(new UnifiedPromoteBuildAction<ArtifactoryGradleConfigurator>(build,
                                                ArtifactoryGradleConfigurator.this));
                            }
                            if (isAllowBintrayPushOfNonStageBuilds()) {
                                build.getActions()
                                        .add(new BintrayPublishAction<ArtifactoryGradleConfigurator>(build,
                                                ArtifactoryGradleConfigurator.this));
                            }
                        }
                    }
                    success = true;
                }
                // Aborted action by the user:
                if (Result.ABORTED.equals(result)) {
                    ConcurrentJobsHelper.concurrentBuildHandler.remove(buildName);
                }

                return success && releaseSuccess;
            }
        };
    }


    private PublisherContext.Builder getBuilder() {
        return new PublisherContext.Builder()
                .artifactoryServer(getArtifactoryServer())
                .deployerOverrider(ArtifactoryGradleConfigurator.this).runChecks(isRunChecks())
                .includePublishArtifacts(isIncludePublishArtifacts())
                .violationRecipients(getViolationRecipients()).scopes(getScopes())
                .licenseAutoDiscovery(isLicenseAutoDiscovery()).discardOldBuilds(isDiscardOldBuilds())
                .deployArtifacts(isDeployArtifacts()).includesExcludes(getArtifactDeploymentPatterns())
                .skipBuildInfoDeploy(!isDeployBuildInfo())
                .includeEnvVars(isIncludeEnvVars()).envVarsPatterns(getEnvVarsPatterns())
                .discardBuildArtifacts(isDiscardBuildArtifacts()).matrixParams(getMatrixParams())
                .artifactsPattern(getArtifactPattern()).ivyPattern(getIvyPattern())
                .deployIvy(isDeployIvy()).deployMaven(isDeployMaven()).maven2Compatible(isM2Compatible())
                .enableIssueTrackerIntegration(isEnableIssueTrackerIntegration())
                .aggregateBuildIssues(isAggregateBuildIssues())
                .aggregationBuildStatus(getAggregationBuildStatus())
                .integrateBlackDuck(isBlackDuckRunChecks(), getBlackDuckAppName(), getBlackDuckAppVersion(),
                        getBlackDuckReportRecipients(), getBlackDuckScopes(),
                        isBlackDuckIncludePublishedArtifacts(), isAutoCreateMissingComponentRequests(),
                        isAutoDiscardStaleComponentRequests())
                .filterExcludedArtifactsFromBuild(isFilterExcludedArtifactsFromBuild());
    }

    public boolean isRelease(AbstractBuild build) {
        boolean actionExists = build.getAction(GradleReleaseAction.class) != null ||
                build.getAction(GradleReleaseApiAction.class) != null;
        return getReleaseWrapper() != null && actionExists;
    }

    private Gradle getLastGradleBuild(AbstractProject project) {
        if (project instanceof Project) {
            List<Gradle> gradles = ActionableHelper.getBuilder((Project) project, Gradle.class);
            return Iterables.getLast(gradles, null);
        }
        return null;
    }

    private void setTargetsField(Gradle builder, String fieldName, String value) {
        try {
            Field targetsField = builder.getClass().getDeclaredField(fieldName);
            targetsField.setAccessible(true);
            targetsField.set(builder, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public ArtifactoryServer getArtifactoryServer() {
        return RepositoriesUtils.getArtifactoryServer(getArtifactoryName(), getDescriptor().getArtifactoryServers());
    }

    public List<PluginSettings> getUserPluginKeys() {
        return getDescriptor().userPluginKeys;
    }

    public List<Repository> getReleaseRepositories() {
        return RepositoriesUtils.collectRepositories(getDescriptor().releaseRepositories, details.getDeployReleaseRepository().getKeyFromSelect());
    }

    public List<VirtualRepository> getVirtualRepositories() {
        return RepositoriesUtils.collectVirtualRepositories(getDescriptor().virtualRepositories, details.getResolveSnapshotRepository().getKeyFromSelect());
    }

    public boolean isOverridingDefaultResolver() {
        return (getOverridingDeployerCredentials() != null);
    }

    public Credentials getOverridingResolverCredentials() {
        return overridingDeployerCredentials;
    }

    public List<UserPluginInfo> getStagingUserPluginInfo() {
        ArtifactoryServer artifactoryServer = getArtifactoryServer();
        return artifactoryServer.getStagingUserPluginInfo(this);
    }

    public PluginSettings getSelectedStagingPlugin() throws Exception {
        return details.getStagingPlugin();
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
        private List<Repository> releaseRepositories;
        private List<VirtualRepository> virtualRepositories;
        private List<PluginSettings> userPluginKeys = Collections.emptyList();
        private AbstractProject<?, ?> item;

        public DescriptorImpl() {
            super(ArtifactoryGradleConfigurator.class);
            load();
        }

        protected DescriptorImpl(Class<? extends BuildWrapper> clazz) {
            super(clazz);
        }

        @Override
        public boolean isApplicable(AbstractProject<?, ?> item) {
            this.item = item;
            return item.getClass().isAssignableFrom(FreeStyleProject.class) ||
                    item.getClass().isAssignableFrom(MatrixProject.class) ||
                    (Jenkins.getInstance().getPlugin(PluginsUtils.MULTIJOB_PLUGIN_ID) != null &&
                            item.getClass().isAssignableFrom(MultiJobProject.class));
        }

        private void refreshRepositories(ArtifactoryServer artifactoryServer, String credentialsUsername, String credentialsPassword, boolean overridingDeployerCredentials) throws IOException {
            List<String> releaseRepositoryKeysFirst = RepositoriesUtils.getLocalRepositories(artifactoryServer.getUrl(), credentialsUsername, credentialsPassword, overridingDeployerCredentials, artifactoryServer);
            Collections.sort(releaseRepositoryKeysFirst);
            releaseRepositories = RepositoriesUtils.createRepositoriesList(releaseRepositoryKeysFirst);
        }

        private void refreshVirtualRepositories(ArtifactoryServer artifactoryServer, String credentialsUsername, String credentialsPassword, boolean overridingDeployerCredentials) throws IOException {
            virtualRepositories = RepositoriesUtils.getVirtualRepositoryKeys(artifactoryServer.getUrl(), credentialsUsername, credentialsPassword, overridingDeployerCredentials, artifactoryServer);
            Collections.sort(virtualRepositories);
        }

        private void refreshUserPlugins(ArtifactoryServer artifactoryServer, final String credentialsUsername, final String credentialsPassword, final boolean overridingDeployerCredentials) {
            List<UserPluginInfo> pluginInfoList = artifactoryServer.getStagingUserPluginInfo(new DeployerOverrider() {
                public boolean isOverridingDefaultDeployer() {
                    return overridingDeployerCredentials;
                }

                public Credentials getOverridingDeployerCredentials() {
                    if (overridingDeployerCredentials && StringUtils.isNotBlank(credentialsUsername) && StringUtils.isNotBlank(credentialsPassword)) {
                        return new Credentials(credentialsUsername, credentialsPassword);
                    }
                    return null;
                }
            });

            ArrayList<PluginSettings> list = new ArrayList<PluginSettings>(pluginInfoList.size());
            for (UserPluginInfo p : pluginInfoList) {
                Map<String, String> paramsMap = Maps.newHashMap();
                List<UserPluginInfoParam> params = p.getPluginParams();
                for (UserPluginInfoParam param : params) {
                    paramsMap.put(((String) param.getKey()), ((String) param.getDefaultValue()));
                }

                PluginSettings plugin = new PluginSettings(p.getPluginName(), paramsMap);
                list.add(plugin);
            }

            userPluginKeys = list;
        }

        /**
         * This method triggered from the client side by Ajax call.
         * The Element that trig is the "Refresh Repositories" button.
         *
         * @param url                           the artifactory url
         * @param credentialsUsername           override credentials user name
         * @param credentialsPassword           override credentials password
         * @param overridingDeployerCredentials user choose to override credentials
         * @return {@link RefreshServerResponse} object that represents the response of the repositories
         */
        @JavaScriptMethod
        public RefreshServerResponse refreshFromArtifactory(String url, String credentialsUsername, String credentialsPassword, boolean overridingDeployerCredentials) {
            RefreshServerResponse response = new RefreshServerResponse();

            try {
                ArtifactoryServer artifactoryServer = RepositoriesUtils.getArtifactoryServer(url, getArtifactoryServers());
                refreshRepositories(artifactoryServer, credentialsUsername, credentialsPassword, overridingDeployerCredentials);
                refreshVirtualRepositories(artifactoryServer, credentialsUsername, credentialsPassword, overridingDeployerCredentials);
                refreshUserPlugins(artifactoryServer, credentialsUsername, credentialsPassword, overridingDeployerCredentials);

                response.setRepositories(releaseRepositories);
                response.setVirtualRepositories(virtualRepositories);
                response.setUserPlugins(userPluginKeys);
                response.setSuccess(true);
            } catch (Exception e) {
                e.printStackTrace();
                response.setResponseMessage(e.getMessage());
                response.setSuccess(false);
            }
            return response;
        }

        @Override
        public String getDisplayName() {
            return "Gradle-Artifactory Integration";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            req.bindParameters(this, "gradle");
            save();
            return true;
        }

        public boolean isMultiConfProject() {
            return (item.getClass().isAssignableFrom(MatrixProject.class));
        }

        @Override
        public BuildWrapper newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            ArtifactoryGradleConfigurator wrapper = (ArtifactoryGradleConfigurator) super.newInstance(req, formData);
            return wrapper;
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

    /**
     * This run listener handles the job completed event to cleanup svn tags and working copy in case of build failure.
     */
    @Extension
    public static final class ReleaseRunListener extends RunListener<AbstractBuild> {
        /**
         * Completed event is sent after the build and publishers execution. The build result in this stage is final and
         * cannot be modified. So this is a good place to revert working copy and tag if the build failed.
         */
        @Override
        public void onCompleted(AbstractBuild run, TaskListener listener) {
            if (!(run instanceof FreeStyleBuild)) {
                return;
            }

            BaseGradleReleaseAction releaseAction = run.getAction(GradleReleaseAction.class);
            if (releaseAction == null) {
                releaseAction = run.getAction(GradleReleaseApiAction.class);
            }
            if (releaseAction == null) {
                return;
            }

            // signal completion to the scm coordinator
            ArtifactoryGradleConfigurator wrapper = ActionableHelper.getBuildWrapper(
                    (BuildableItemWithBuildWrappers) run.getProject(), ArtifactoryGradleConfigurator.class);

            Result result = run.getResult();
            boolean successRun = result.isBetterOrEqualTo(Result.SUCCESS);

            if (!wrapper.isAllowPromotionOfNonStagedBuilds()) {
                if (successRun) {
                    // add a stage action
                    run.addAction(new UnifiedPromoteBuildAction<ArtifactoryGradleConfigurator>(run, wrapper));
                }
            }

            if (!wrapper.isAllowBintrayPushOfNonStageBuilds()) {
                if (successRun) {
                    // add push to bintray action
                    run.addAction(new BintrayPublishAction<ArtifactoryGradleConfigurator>(run, wrapper));
                }
            }

            try {
                wrapper.getReleaseWrapper().getScmCoordinator().buildCompleted();
            } catch (Exception e) {
                run.setResult(Result.FAILURE);
                listener.error("[RELEASE] Failed on build completion");
                e.printStackTrace(listener.getLogger());
            }

            // once the build is completed reset the version maps. Since the GradleReleaseAction is saved
            // in memory and is only build when re-saving a project's config or during startup, therefore
            // a cleanup of the internal maps is needed.
            releaseAction.reset();

            // remove the release action from the build. the stage action is the point of interaction for successful builds
            run.getActions().remove(releaseAction);
        }
    }
}
