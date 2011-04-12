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
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.model.listeners.RunListener;
import hudson.plugins.gradle.Gradle;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.FormValidation;
import hudson.util.XStream2;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jfrog.build.ArtifactoryPluginUtils;
import org.jfrog.hudson.ArtifactoryBuilder;
import org.jfrog.hudson.ArtifactoryServer;
import org.jfrog.hudson.BuildInfoResultAction;
import org.jfrog.hudson.DeployerOverrider;
import org.jfrog.hudson.ServerDetails;
import org.jfrog.hudson.action.ActionableHelper;
import org.jfrog.hudson.action.ArtifactoryProjectAction;
import org.jfrog.hudson.release.GradlePromoteBuildAction;
import org.jfrog.hudson.release.ReleaseAction;
import org.jfrog.hudson.release.gradle.GradleReleaseAction;
import org.jfrog.hudson.release.gradle.GradleReleaseWrapper;
import org.jfrog.hudson.util.BuildContext;
import org.jfrog.hudson.util.Credentials;
import org.jfrog.hudson.util.ExtractorUtils;
import org.jfrog.hudson.util.FormValidations;
import org.jfrog.hudson.util.IncludesExcludes;
import org.jfrog.hudson.util.OverridingDeployerCredentialsConverter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;


/**
 * Gradle-Artifactory plugin configuration, allows to add the server details, deployment username/password, as well as
 * flags to deploy ivy, maven, and artifacts, as well as specifications of the location of the remote plugin (.gradle)
 * groovy script.
 *
 * @author Tomer Cohen
 */
public class ArtifactoryGradleConfigurator extends BuildWrapper implements DeployerOverrider {
    private ServerDetails details;
    private boolean deployArtifacts;
    private final Credentials overridingDeployerCredentials;
    public final boolean deployMaven;
    public final boolean deployIvy;
    public final String remotePluginLocation;
    public final boolean deployBuildInfo;
    public final boolean includeEnvVars;
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
    private final GradleReleaseWrapper releaseWrapper;
    private final boolean discardBuildArtifacts;


    @DataBoundConstructor
    public ArtifactoryGradleConfigurator(ServerDetails details, Credentials overridingDeployerCredentials,
            boolean deployMaven, boolean deployIvy, boolean deployArtifacts, String remotePluginLocation,
            boolean includeEnvVars, boolean deployBuildInfo, boolean runChecks, String violationRecipients,
            boolean includePublishArtifacts, String scopes, boolean disableLicenseAutoDiscovery, String ivyPattern,
            String artifactPattern, boolean notM2Compatible, IncludesExcludes artifactDeploymentPatterns,
            boolean discardOldBuilds, boolean passIdentifiedDownstream, GradleReleaseWrapper releaseWrapper,
            boolean discardBuildArtifacts) {
        this.details = details;
        this.overridingDeployerCredentials = overridingDeployerCredentials;
        this.deployMaven = deployMaven;
        this.deployIvy = deployIvy;
        this.deployArtifacts = deployArtifacts;
        this.remotePluginLocation = remotePluginLocation;
        this.includeEnvVars = includeEnvVars;
        this.deployBuildInfo = deployBuildInfo;
        this.runChecks = runChecks;
        this.violationRecipients = violationRecipients;
        this.includePublishArtifacts = includePublishArtifacts;
        this.scopes = scopes;
        this.disableLicenseAutoDiscovery = disableLicenseAutoDiscovery;
        this.ivyPattern = ivyPattern;
        this.artifactPattern = artifactPattern;
        this.notM2Compatible = notM2Compatible;
        this.artifactDeploymentPatterns = artifactDeploymentPatterns;
        this.discardOldBuilds = discardOldBuilds;
        this.passIdentifiedDownstream = passIdentifiedDownstream;
        this.releaseWrapper = releaseWrapper;
        this.discardBuildArtifacts = discardBuildArtifacts;
        this.licenseAutoDiscovery = !disableLicenseAutoDiscovery;
    }

    public GradleReleaseWrapper getReleaseWrapper() {
        return releaseWrapper;
    }

    public ServerDetails getDetails() {
        return details;
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
        return artifactPattern;
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

    public String getRepositoryKey() {
        return details != null ? details.repositoryKey : null;
    }

    public String getDownloadRepositoryKey() {
        return details != null ? details.downloadRepositoryKey : null;
    }

    public String getArtifactoryName() {
        return details != null ? details.artifactoryName : null;
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

    @Override
    public Collection<? extends Action> getProjectActions(AbstractProject project) {
        List<ArtifactoryProjectAction> action =
                ActionableHelper.getArtifactoryProjectAction(details.artifactoryName, project);
        if (getReleaseWrapper() != null) {
            List actions = new ArrayList();
            actions.add(new GradleReleaseAction((FreeStyleProject) project));
            actions.addAll(action);
            return actions;
        }
        return action;
    }

    @Override
    public Environment setUp(final AbstractBuild build, Launcher launcher, BuildListener listener)
            throws IOException, InterruptedException {
        if (isRelease(build)) {
            releaseWrapper.setUp(build, launcher, listener);
        }
        ArtifactoryServer artifactoryServer = getArtifactoryServer();
        if (artifactoryServer == null) {
            listener.getLogger().format("No Artifactory server configured for %s. " +
                    "Please check your configuration.", getArtifactoryName()).println();
            build.setResult(Result.FAILURE);
            return new Environment() {
            };
        }
        GradleInitScriptWriter writer = new GradleInitScriptWriter(build);
        FilePath workspace = build.getWorkspace();
        FilePath initScript;
        try {
            initScript = workspace.createTextTempFile("init-artifactory", "gradle", writer.generateInitScript(), false);
        } catch (Exception e) {
            listener.getLogger().println("Error occurred while writing Gradle Init Script: " + e.getMessage());
            build.setResult(Result.FAILURE);
            return new Environment() {
            };
        }

        String initScriptPath = initScript.getRemote();
        initScriptPath = initScriptPath.replace('\\', '/');
        final Gradle gradleBuild = getLastGradleBuild(build.getProject());
        String originalTasks = null;
        String switches = null;
        if (gradleBuild != null) {
            switches = gradleBuild.getSwitches() + "";
            originalTasks = gradleBuild.getTasks() + "";
            final String tasks;
            if (isRelease(build)) {
                String alternativeGoals = releaseWrapper.getAlternativeTasks();
                if (StringUtils.isNotBlank(alternativeGoals)) {
                    tasks = alternativeGoals;
                } else {
                    tasks = gradleBuild.getTasks() + "";
                }
            } else {
                tasks = gradleBuild.getTasks() + "";
            }
            setTargetsField(gradleBuild, "switches", switches + " " + "--init-script " + initScriptPath);
            setTargetsField(gradleBuild, "tasks", tasks + " " + ArtifactoryPluginUtils.BUILD_INFO_TASK_NAME);
        } else {
            listener.getLogger().println("[Warning] No Gradle build configured");
        }
        final String finalSwitches = switches;
        final String finalOriginalTasks = originalTasks;
        return new Environment() {
            @Override
            public void buildEnvVars(Map<String, String> env) {
                ServerDetails serverDetails = getDetails();
                ReleaseAction releaseAction = ActionableHelper.getLatestAction(build, ReleaseAction.class);
                if (releaseAction != null && StringUtils.isNotBlank(releaseAction.getStagingRepositoryKey())) {
                    serverDetails = new ServerDetails(
                            serverDetails.artifactoryName, releaseAction.getStagingRepositoryKey(),
                            serverDetails.snapshotsRepositoryKey, serverDetails.downloadRepositoryKey);
                }
                final BuildContext context = new BuildContext(serverDetails, ArtifactoryGradleConfigurator.this,
                        isRunChecks(), isIncludePublishArtifacts(), getViolationRecipients(), getScopes(),
                        isLicenseAutoDiscovery(), isDiscardOldBuilds(), isDeployArtifacts(),
                        getArtifactDeploymentPatterns(), !isDeployBuildInfo(), isIncludeEnvVars(),
                        isDiscardBuildArtifacts());
                context.setArtifactsPattern(getArtifactPattern());
                context.setIvyPattern(getIvyPattern());
                context.setDeployIvy(isDeployIvy());
                context.setDeployMaven(isDeployMaven());
                try {
                    ExtractorUtils.addBuilderInfoArguments(env, build, getArtifactoryServer(), context);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public boolean tearDown(AbstractBuild build, BuildListener listener)
                    throws IOException, InterruptedException {
                boolean success = false;
                boolean releaseSuccess = true;
                if (isRelease(build)) {
                    releaseSuccess = releaseWrapper.tearDown(build, listener);
                }
                if (gradleBuild != null) {
                    setTargetsField(gradleBuild, "switches", finalSwitches);
                    setTargetsField(gradleBuild, "tasks", finalOriginalTasks);
                }
                Result result = build.getResult();
                if (result != null && result.isBetterOrEqualTo(Result.SUCCESS)) {
                    if (isDeployBuildInfo()) {
                        build.getActions().add(new BuildInfoResultAction(getArtifactoryName(), build));
                    }
                    success = true;
                }
                return success && releaseSuccess;
            }
        };
    }

    public boolean isRelease(AbstractBuild build) {
        return getReleaseWrapper() != null && build.getAction(GradleReleaseAction.class) != null;
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

    @Extension(optional = true)
    public static class DescriptorImpl extends BuildWrapperDescriptor {
        public DescriptorImpl() {
            super(ArtifactoryGradleConfigurator.class);
            load();
        }

        @Override
        public boolean isApplicable(AbstractProject<?, ?> item) {
            return item.getClass().isAssignableFrom(FreeStyleProject.class);
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

        public FormValidation doCheckViolationRecipients(@QueryParameter String value) {
            return FormValidations.validateEmails(value);
        }

        /**
         * @param baseTagUrl The subversion tags url
         * @return Error message if tags url is not set
         */
        @SuppressWarnings({"UnusedDeclaration"})
        public FormValidation doCheckBaseTagUrl(@QueryParameter String baseTagUrl) {
            String trimmedUrl = hudson.Util.fixEmptyAndTrim(baseTagUrl);
            if (trimmedUrl == null) {
                return FormValidation.error("Subversion base tags URL is mandatory");
            }
            return FormValidation.ok();
        }

        /**
         * @param tagPrefix The subversion tags url
         * @return Error message if tags url is not set
         */
        @SuppressWarnings({"UnusedDeclaration"})
        public FormValidation doCheckTagPrefix(@QueryParameter String tagPrefix) {
            return FormValidations.validateTagPrefix(tagPrefix);
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

            GradleReleaseAction releaseAction = run.getAction(GradleReleaseAction.class);
            if (releaseAction == null) {
                return;
            }

            Result result = run.getResult();
            if (result.isBetterOrEqualTo(Result.SUCCESS)) {
                // add a stage action
                run.addAction(new GradlePromoteBuildAction(run));
            }

            // signal completion to the scm coordinator
            ArtifactoryGradleConfigurator wrapper = ActionableHelper.getBuildWrapper(
                    (BuildableItemWithBuildWrappers) run.getProject(), ArtifactoryGradleConfigurator.class);
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
     * @deprecated: Use org.jfrog.hudson.DeployerOverrider#getOverridingDeployerCredentials()
     */
    @Deprecated
    private transient String username;

    /**
     * @deprecated: Use org.jfrog.hudson.DeployerOverrider#getOverridingDeployerCredentials()
     */
    @Deprecated
    private transient String scrambledPassword;
}
