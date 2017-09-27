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
import hudson.util.ListBoxModel;
import hudson.util.XStream2;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jfrog.gradle.plugin.artifactory.task.BuildInfoBaseTask;
import org.jfrog.hudson.*;
import org.jfrog.hudson.BintrayPublish.BintrayPublishAction;
import org.jfrog.hudson.action.ActionableHelper;
import org.jfrog.hudson.action.ArtifactoryProjectAction;
import org.jfrog.hudson.pipeline.Utils;
import org.jfrog.hudson.release.ReleaseAction;
import org.jfrog.hudson.release.gradle.BaseGradleReleaseAction;
import org.jfrog.hudson.release.gradle.GradleReleaseAction;
import org.jfrog.hudson.release.gradle.GradleReleaseApiAction;
import org.jfrog.hudson.release.gradle.GradleReleaseWrapper;
import org.jfrog.hudson.release.promotion.UnifiedPromoteBuildAction;
import org.jfrog.hudson.util.*;
import org.jfrog.hudson.util.converters.DeployerResolverOverriderConverter;
import org.jfrog.hudson.util.plugins.MultiConfigurationUtils;
import org.jfrog.hudson.util.plugins.PluginsUtils;
import org.jfrog.hudson.util.publisher.PublisherContext;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.bind.JavaScriptMethod;

import java.io.IOException;
import java.io.PrintStream;
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
    private final CredentialsConfig deployerCredentialsConfig;
    private final CredentialsConfig resolverCredentialsConfig;
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
    private final boolean asyncBuildRetention;
    private final String matrixParams;
    private final boolean skipInjectInitScript;
    private final boolean allowPromotionOfNonStagedBuilds;
    private final boolean allowBintrayPushOfNonStageBuilds;
    private final boolean blackDuckRunChecks;
    private final String blackDuckAppName;
    private final String blackDuckAppVersion;
    private final boolean filterExcludedArtifactsFromBuild;
    private final ServerDetails resolverDetails;
    private String defaultPromotionTargetRepository;
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
    private String customBuildName;
    private boolean overrideBuildName;

    /**
     * @deprecated: Use org.jfrog.hudson.gradle.ArtifactoryGradleConfigurator#getDeployerCredentialsConfig()
     */
    @Deprecated
    private Credentials overridingDeployerCredentials;
    /**
     * @deprecated: Use org.jfrog.hudson.gradle.ArtifactoryGradleConfigurator#getResolverCredentialsConfig()
     */
    @Deprecated
    private Credentials overridingResolverCredentials;

    @DataBoundConstructor
    public ArtifactoryGradleConfigurator(ServerDetails details, ServerDetails resolverDetails,
                                         CredentialsConfig deployerCredentialsConfig, CredentialsConfig resolverCredentialsConfig,
                                         boolean deployMaven, boolean deployIvy, boolean deployArtifacts,
                                         String remotePluginLocation, boolean includeEnvVars,
                                         IncludesExcludes envVarsPatterns, boolean deployBuildInfo, boolean runChecks,
                                         String violationRecipients, boolean includePublishArtifacts, String scopes,
                                         boolean disableLicenseAutoDiscovery, String ivyPattern, String artifactPattern,
                                         boolean notM2Compatible, IncludesExcludes artifactDeploymentPatterns,
                                         boolean discardOldBuilds, boolean passIdentifiedDownstream,
                                         GradleReleaseWrapper releaseWrapper, boolean discardBuildArtifacts, boolean asyncBuildRetention,
                                         String matrixParams, boolean skipInjectInitScript,
                                         boolean enableIssueTrackerIntegration, boolean aggregateBuildIssues,
                                         String aggregationBuildStatus, boolean allowPromotionOfNonStagedBuilds,
                                         String defaultPromotionTargetRepository,
                                         boolean allowBintrayPushOfNonStageBuilds, boolean blackDuckRunChecks,
                                         String blackDuckAppName, String blackDuckAppVersion,
                                         String blackDuckReportRecipients, String blackDuckScopes,
                                         boolean blackDuckIncludePublishedArtifacts,
                                         boolean autoCreateMissingComponentRequests,
                                         boolean autoDiscardStaleComponentRequests,
                                         boolean filterExcludedArtifactsFromBuild, String artifactoryCombinationFilter,
                                         String customBuildName, boolean overrideBuildName) {
        this.details = details;
        this.resolverDetails = resolverDetails;
        this.deployerCredentialsConfig = deployerCredentialsConfig;
        this.resolverCredentialsConfig = resolverCredentialsConfig;
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
        this.asyncBuildRetention = asyncBuildRetention;
        this.discardBuildArtifacts = discardBuildArtifacts;
        this.matrixParams = matrixParams;
        this.skipInjectInitScript = skipInjectInitScript;
        this.licenseAutoDiscovery = !disableLicenseAutoDiscovery;
        this.allowPromotionOfNonStagedBuilds = allowPromotionOfNonStagedBuilds;
        this.defaultPromotionTargetRepository = defaultPromotionTargetRepository;
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
        this.customBuildName = customBuildName;
        this.overrideBuildName = overrideBuildName;
    }

    public GradleReleaseWrapper getReleaseWrapper() {
        return releaseWrapper;
    }

    public ServerDetails getDetails() {
        return details;
    }

    public ServerDetails getResolverDetails() {
        return resolverDetails;
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

    public boolean isAsyncBuildRetention() {
        return asyncBuildRetention;
    }

    public boolean isSkipInjectInitScript() {
        return skipInjectInitScript;
    }

    public boolean isOverridingDefaultDeployer() {
        return deployerCredentialsConfig != null && deployerCredentialsConfig.isCredentialsProvided();
    }

    public Credentials getOverridingDeployerCredentials() {
        return overridingDeployerCredentials;
    }

    public CredentialsConfig getDeployerCredentialsConfig() {
        return deployerCredentialsConfig;
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

    public String getArtifactoryResolverName() {
        return resolverDetails != null ? resolverDetails.artifactoryName : null;
    }

    public String getArtifactoryUrl() {
        ArtifactoryServer server = getArtifactoryServer();
        return server != null ? server.getUrl() : null;
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

    public String getDefaultPromotionTargetRepository() {
        return defaultPromotionTargetRepository;
    }

    public void setDefaultPromotionTargetRepository(String defaultPromotionTargetRepository) {
        this.defaultPromotionTargetRepository = defaultPromotionTargetRepository;
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

    public String getCustomBuildName() {
        return customBuildName;
    }

    public boolean isOverrideBuildName() {
        return overrideBuildName;
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
        List<ArtifactoryProjectAction> action = null;
        if (isOverrideBuildName()) {
            action = ActionableHelper.getArtifactoryProjectAction(getArtifactoryName(), project, getCustomBuildName());
        } else {
            action = ActionableHelper.getArtifactoryProjectAction(getArtifactoryName(), project);
        }
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
    public Environment setUp(final AbstractBuild build, final Launcher launcher, final BuildListener listener)
            throws IOException, InterruptedException {
        final PrintStream log = listener.getLogger();
        log.println("Jenkins Artifactory Plugin version: " + ActionableHelper.getArtifactoryPluginVersion());
        PublisherContext.Builder publisherBuilder = getBuilder();
        RepositoriesUtils.validateServerConfig(build, listener, getArtifactoryServer(), getArtifactoryUrl());

        int totalBuilds = 1;

        if (isMultiConfProject(build)) {
            totalBuilds = ((MatrixProject) build.getParent().getParent()).getActiveConfigurations().size();
            if (isDeployArtifacts()) {
                if (StringUtils.isBlank(getArtifactoryCombinationFilter())) {
                    String error = "The field \"Combination Matches\" is empty, but is defined as mandatory!";
                    log.println(error);
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
            new ConcurrentJobsHelper.ConcurrentBuildSetupSync(build, totalBuilds) {
                @Override
                public void setUp() {
                    // Obtain the current build and use it to store the configured switches and tasks.
                    // We store them because we override them during the build and we'll need
                    // their original values at the tear down stage so that they can be restored.
                    ConcurrentJobsHelper.ConcurrentBuild concurrentBuild = ConcurrentJobsHelper.getConcurrentBuild(build);

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
            log.println("[Warning] No Gradle build configured");
        }

        final PublisherContext.Builder finalPublisherBuilder = publisherBuilder;

        return new Environment() {
            String initScriptPath;

            @Override
            public void buildEnvVars(Map<String, String> env) {
                GradleInitScriptWriter writer = new GradleInitScriptWriter(ActionableHelper.getNode(launcher).getRootPath());
                FilePath workspace = build.getWorkspace();
                FilePath initScript;
                try {
                    initScript = workspace.createTextTempFile("init-artifactory", "gradle",
                            writer.generateInitScript(), false);
                    ActionableHelper.deleteFilePathOnExit(initScript);
                    initScriptPath = initScript.getRemote();
                    initScriptPath = initScriptPath.replace('\\', '/');
                    env.put("ARTIFACTORY_INIT_SCRIPT", " --init-script " + initScriptPath);

                } catch (Exception e) {
                    log.println("Error occurred while writing Gradle Init Script: " + e.getMessage());
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
                serverDetails = releaseActionOverride(env, serverDetails);
                finalPublisherBuilder.serverDetails(serverDetails);

                ServerDetails resolverServerDetails = getResolverDetails();
                ResolverContext resolverContext = null;
                if (StringUtils.isNotBlank(resolverServerDetails.getResolveReleaseRepository().getRepoKey())) {
                    CredentialsConfig resolverCredentials = CredentialManager.getPreferredResolver(
                            ArtifactoryGradleConfigurator.this, getArtifactoryServer());
                    resolverContext = new ResolverContext(getArtifactoryResolverServer(), resolverServerDetails,
                            resolverCredentials.getCredentials(build.getProject()), ArtifactoryGradleConfigurator.this);
                }

                try {
                    ExtractorUtils.addBuilderInfoArguments(env, build, listener,
                            finalPublisherBuilder.build(), resolverContext, build.getWorkspace(), launcher);
                } catch (Exception e) {
                    log.println(e.getMessage());
                    throw new RuntimeException(e);
                }
            }

            @Override
            public boolean tearDown(final AbstractBuild build, BuildListener listener)
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
                    new ConcurrentJobsHelper.ConcurrentBuildTearDownSync(build, result) {
                        @Override
                        public void tearDown() {
                            // Restore the original switches and tasks of this build (we overrided their
                            // values in the setUp stage):
                            ConcurrentJobsHelper.ConcurrentBuild concurrentBuild = ConcurrentJobsHelper.getConcurrentBuild(build);
                            String switches = concurrentBuild.getParam("switches");
                            String tasks = concurrentBuild.getParam("tasks");
                            switches = switches.replace("${ARTIFACTORY_INIT_SCRIPT}", "");
                            tasks = tasks.replace("${ARTIFACTORY_TASKS}", "");
                            setTargetsField(gradleBuild, "switches", switches);
                            setTargetsField(gradleBuild, "tasks", tasks);
                            try {
                                ActionableHelper.deleteFilePath(build.getWorkspace(), initScriptPath);
                            } catch (IOException e) {
                                log.println(e.getStackTrace());
                            }
                        }
                    };
                }
                if (result != null && result.isBetterOrEqualTo(Result.SUCCESS)) {
                    if (isDeployBuildInfo()) {
                        String buildName = BuildUniqueIdentifierHelper.getBuildNameConsiderOverride(ArtifactoryGradleConfigurator.this, build);
                        build.getActions().add(new BuildInfoResultAction(getArtifactoryUrl(), build, buildName));
                        ArtifactoryGradleConfigurator configurator =
                                ActionableHelper.getBuildWrapper(build.getProject(),
                                        ArtifactoryGradleConfigurator.class);
                        if (configurator != null) {
                            if (isAllowPromotionOfNonStagedBuilds()) {
                                build.getActions()
                                        .add(new UnifiedPromoteBuildAction(build, ArtifactoryGradleConfigurator.this));
                            }
                            // Checks if Push to Bintray is disabled.
                            if (PluginsUtils.isPushToBintrayEnabled()) {
                                if (isAllowBintrayPushOfNonStageBuilds()) {
                                    build.getActions()
                                            .add(new BintrayPublishAction<ArtifactoryGradleConfigurator>(build,
                                                    ArtifactoryGradleConfigurator.this));
                                }
                            }
                        }
                    }
                    success = true;
                }
                // Aborted action by the user:
                if (Result.ABORTED.equals(result)) {
                    ConcurrentJobsHelper.removeConcurrentBuildJob(build);
                }

                return success && releaseSuccess;
            }

            private ServerDetails releaseActionOverride(Map<String, String> env, ServerDetails serverDetails) {
                ReleaseAction releaseAction = ActionableHelper.getLatestAction(build, ReleaseAction.class);
                if (releaseAction != null) {
                    releaseAction.addVars(env);
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
                return serverDetails;
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
                .discardBuildArtifacts(isDiscardBuildArtifacts()).asyncBuildRetention(isAsyncBuildRetention())
                .matrixParams(getMatrixParams()).artifactsPattern(getArtifactPattern()).ivyPattern(getIvyPattern())
                .deployIvy(isDeployIvy()).deployMaven(isDeployMaven()).maven2Compatible(isM2Compatible())
                .enableIssueTrackerIntegration(isEnableIssueTrackerIntegration())
                .aggregateBuildIssues(isAggregateBuildIssues())
                .aggregationBuildStatus(getAggregationBuildStatus())
                .integrateBlackDuck(isBlackDuckRunChecks(), getBlackDuckAppName(), getBlackDuckAppVersion(),
                        getBlackDuckReportRecipients(), getBlackDuckScopes(),
                        isBlackDuckIncludePublishedArtifacts(), isAutoCreateMissingComponentRequests(),
                        isAutoDiscardStaleComponentRequests())
                .filterExcludedArtifactsFromBuild(isFilterExcludedArtifactsFromBuild())
                .artifactoryPluginVersion(ActionableHelper.getArtifactoryPluginVersion())
                .overrideBuildName(isOverrideBuildName())
                .customBuildName(getCustomBuildName());
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

    public ArtifactoryServer getArtifactoryResolverServer() {
        return RepositoriesUtils.getArtifactoryServer(getArtifactoryResolverName(),
                getDescriptor().getArtifactoryServers());
    }

    public List<Repository> getReleaseRepositories() {
        return RepositoriesUtils.collectRepositories(details.getDeployReleaseRepository().getKeyFromSelect());
    }

    public List<VirtualRepository> getVirtualRepositories() {
        return RepositoriesUtils.collectVirtualRepositories(null, resolverDetails.getResolveSnapshotRepository().getKeyFromSelect());
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

    public List<UserPluginInfo> getStagingUserPluginInfo() {
        ArtifactoryServer artifactoryServer = getArtifactoryServer();
        return artifactoryServer.getStagingUserPluginInfo(this, null);
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

        private List<Repository> refreshRepositories(ArtifactoryServer artifactoryServer, CredentialsConfig credentialsConfig)
                throws IOException {
            List<String> releaseRepositoryKeysFirst = RepositoriesUtils.getLocalRepositories(artifactoryServer.getUrl(),
                    credentialsConfig, artifactoryServer, item);
            Collections.sort(releaseRepositoryKeysFirst);
            List<Repository> releaseRepositories = RepositoriesUtils.createRepositoriesList(releaseRepositoryKeysFirst);
            return releaseRepositories;
        }

        private List<VirtualRepository> refreshVirtualRepositories(ArtifactoryServer artifactoryServer,
                                                                   CredentialsConfig credentialsConfig) throws IOException {
            List<VirtualRepository> virtualRepositories = RepositoriesUtils.getVirtualRepositoryKeys(artifactoryServer.getUrl(),
                    credentialsConfig, artifactoryServer, item);
            Collections.sort(virtualRepositories);
            return virtualRepositories;
        }

        private List<PluginSettings> refreshUserPlugins(ArtifactoryServer artifactoryServer, final CredentialsConfig credentialsConfigs) {
            List<UserPluginInfo> pluginInfoList = artifactoryServer.getStagingUserPluginInfo(new DeployerOverrider() {
                public boolean isOverridingDefaultDeployer() {
                    return credentialsConfigs != null && credentialsConfigs.isCredentialsProvided();
                }

                public Credentials getOverridingDeployerCredentials() {
                    return null;
                }

                public CredentialsConfig getDeployerCredentialsConfig() {
                    return credentialsConfigs;
                }
            }, item);

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

            return list;
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
        @SuppressWarnings("unused")
        @JavaScriptMethod
        public RefreshServerResponse refreshFromArtifactory(String url, String credentialsId, String username, String password, boolean overrideCredentials) {
            RefreshServerResponse response = new RefreshServerResponse();
            CredentialsConfig credentialsConfig = new CredentialsConfig(username, password, credentialsId, overrideCredentials);
            try {
                ArtifactoryServer artifactoryServer = RepositoriesUtils.getArtifactoryServer(
                        url, getArtifactoryServers());
                List<Repository> releaseRepositories = refreshRepositories(artifactoryServer, credentialsConfig);
                List<PluginSettings> userPluginKeys = refreshUserPlugins(artifactoryServer, credentialsConfig);

                response.setRepositories(releaseRepositories);
                response.setUserPlugins(userPluginKeys);
                response.setSuccess(true);
            } catch (Exception e) {
                response.setResponseMessage(e.getMessage());
                response.setSuccess(false);
            }
            return response;
        }

        /**
         * This method is triggered from the client side by ajax call.
         * The method is triggered by the "Refresh Repositories" button.
         *
         * @param url           Artifactory url
         * @param credentialsId credentials Id if using Credentials plugin
         * @param username      credentials legacy mode username
         * @param password      credentials legacy mode password
         * @return {@link org.jfrog.hudson.util.RefreshServerResponse} object that represents the response of the repositories
         */
        @SuppressWarnings("unused")
        @JavaScriptMethod
        public RefreshServerResponse refreshResolversFromArtifactory(String url, String credentialsId,
                                                                     String username, String password, boolean overrideCredentials) {
            RefreshServerResponse response = new RefreshServerResponse();
            CredentialsConfig credentialsConfig = new CredentialsConfig(username, password, credentialsId, overrideCredentials);
            ArtifactoryServer artifactoryServer = RepositoriesUtils.getArtifactoryServer(url, RepositoriesUtils.getArtifactoryServers());

            try {
                List<VirtualRepository> virtualRepositories = refreshVirtualRepositories(artifactoryServer, credentialsConfig);
                response.setVirtualRepositories(virtualRepositories);
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
            return "Gradle-Artifactory Integration";
        }

        public boolean isPushToBintrayEnabled() {
            return PluginsUtils.isPushToBintrayEnabled();
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

        public boolean isUseCredentialsPlugin() {
            return PluginsUtils.isUseCredentialsPlugin();
        }

        public boolean isJiraPluginEnabled() {
            return (Jenkins.getInstance().getPlugin("jira") != null);
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
                    run.addAction(new UnifiedPromoteBuildAction(run, wrapper));
                }
            }
            // Checks if Push to Bintray is disabled.
            if (PluginsUtils.isPushToBintrayEnabled()) {
                if (!wrapper.isAllowBintrayPushOfNonStageBuilds()) {
                    if (successRun) {
                        // add push to bintray action
                        run.addAction(new BintrayPublishAction<ArtifactoryGradleConfigurator>(run, wrapper));
                    }
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

    /**
     * Page Converter
     */
    public static final class ConverterImpl extends DeployerResolverOverriderConverter {
        public ConverterImpl(XStream2 xstream) {
            super(xstream);
        }
    }
}
