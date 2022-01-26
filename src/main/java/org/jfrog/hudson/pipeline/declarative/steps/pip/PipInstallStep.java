package org.jfrog.hudson.pipeline.declarative.steps.pip;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import hudson.Extension;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jfrog.hudson.pipeline.ArtifactorySynchronousNonBlockingStepExecution;
import org.jfrog.hudson.pipeline.common.executors.PipInstallExecutor;
import org.jfrog.hudson.pipeline.common.types.ArtifactoryServer;
import org.jfrog.hudson.pipeline.common.types.buildInfo.BuildInfo;
import org.jfrog.hudson.pipeline.common.types.builds.PipBuild;
import org.jfrog.hudson.pipeline.common.types.resolvers.CommonResolver;
import org.jfrog.hudson.pipeline.declarative.BuildDataFile;
import org.jfrog.hudson.pipeline.declarative.utils.DeclarativePipelineUtils;
import org.jfrog.hudson.util.BuildUniqueIdentifierHelper;
import org.jfrog.hudson.util.JenkinsBuildInfoLog;
import org.jfrog.hudson.util.SerializationUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;

/**
 * Created by Bar Belity on 08/07/2020.
 */
public class PipInstallStep extends AbstractStepImpl {
    static final String STEP_NAME = "rtPipInstall";
    private final PipBuild pipBuild;
    private String customBuildNumber;
    private String customBuildName;
    private String project;
    private String resolverId;
    private String javaArgs; // Added to allow java remote debugging
    private String args;
    private String envActivation;
    private String module;

    @DataBoundConstructor
    public PipInstallStep() {
        this.pipBuild = new PipBuild();
    }

    @DataBoundSetter
    public void setBuildNumber(String customBuildNumber) {
        this.customBuildNumber = customBuildNumber;
    }

    @DataBoundSetter
    public void setBuildName(String customBuildName) {
        this.customBuildName = customBuildName;
    }

    @DataBoundSetter
    public void setProject(String customProject) {
        this.project = customProject;
    }

    @DataBoundSetter
    public void setResolverId(String resolverId) {
        this.resolverId = resolverId;
    }

    @DataBoundSetter
    public void setJavaArgs(String javaArgs) {
        this.javaArgs = javaArgs;
    }

    @DataBoundSetter
    public void setEnvActivation(String envActivation) {
        this.envActivation = envActivation;
    }

    @DataBoundSetter
    public void setModule(String module) {
        this.module = module;
    }

    @DataBoundSetter
    public void setArgs(String args) {
        this.args = args;
    }

    public static class Execution extends ArtifactorySynchronousNonBlockingStepExecution<Void> {

        private transient final PipInstallStep step;

        @Inject
        public Execution(PipInstallStep step, StepContext context) throws IOException, InterruptedException {
            super(context);
            this.step = step;
        }

        @Override
        protected Void runStep() throws Exception {
            BuildInfo buildInfo = DeclarativePipelineUtils.getBuildInfo(rootWs, build, step.customBuildName, step.customBuildNumber, step.project);
            CommonResolver resolver = getResolver(BuildUniqueIdentifierHelper.getBuildNumber(build));
            step.pipBuild.setResolver(resolver);
            PipInstallExecutor pipInstallExecutor = new PipInstallExecutor(buildInfo, launcher, step.pipBuild, step.javaArgs, step.args, ws, step.envActivation, step.module, env, listener, build);
            pipInstallExecutor.execute();
            DeclarativePipelineUtils.saveBuildInfo(pipInstallExecutor.getBuildInfo(), rootWs, build, new JenkinsBuildInfoLog(listener));
            return null;
        }

        @Override
        public org.jfrog.hudson.ArtifactoryServer getUsageReportServer() throws IOException, InterruptedException {
            CommonResolver resolver = getResolver(BuildUniqueIdentifierHelper.getBuildNumber(build));
            if (resolver != null) {
                return resolver.getArtifactoryServer();
            }
            return null;
        }

        @Override
        public String getUsageReportFeatureName() {
            return STEP_NAME;
        }

        private CommonResolver getResolver(String buildNumber) throws IOException, InterruptedException {
            if (StringUtils.isBlank(step.resolverId)) {
                return null;
            }
            BuildDataFile buildDataFile = DeclarativePipelineUtils.readBuildDataFile(rootWs, buildNumber, PipResolverStep.STEP_NAME, step.resolverId);
            if (buildDataFile == null) {
                throw new IOException("Resolver " + step.resolverId + " doesn't exist!");
            }
            CommonResolver resolver = SerializationUtils.createMapper().treeToValue(buildDataFile.get(PipResolverStep.STEP_NAME), CommonResolver.class);
            resolver.setServer(getArtifactoryServer(buildDataFile));
            return resolver;
        }

        private ArtifactoryServer getArtifactoryServer(BuildDataFile buildDataFile) throws IOException, InterruptedException {
            JsonNode serverId = buildDataFile.get("serverId");
            if (serverId.isNull()) {
                throw new IllegalArgumentException("server ID is missing");
            }
            return DeclarativePipelineUtils.getArtifactoryServer(build, rootWs, serverId.asText(), true);
        }
    }

    @Extension
    public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super( PipInstallStep.Execution.class);
        }

        @Override
        public String getFunctionName() {
            return "rtPipInstall";
        }

        @Override
        public String getDisplayName() {
            return "run Artifactory pip install";
        }

        @Override
        public boolean isAdvanced() {
            return true;
        }
    }
}
