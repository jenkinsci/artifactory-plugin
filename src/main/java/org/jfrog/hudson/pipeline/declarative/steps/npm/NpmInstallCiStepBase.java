package org.jfrog.hudson.pipeline.declarative.steps.npm;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jfrog.hudson.pipeline.ArtifactorySynchronousNonBlockingStepExecution;
import org.jfrog.hudson.pipeline.common.Utils;
import org.jfrog.hudson.pipeline.common.executors.NpmInstallCiExecutor;
import org.jfrog.hudson.pipeline.common.types.ArtifactoryServer;
import org.jfrog.hudson.pipeline.common.types.buildInfo.BuildInfo;
import org.jfrog.hudson.pipeline.common.types.builds.NpmBuild;
import org.jfrog.hudson.pipeline.common.types.resolvers.CommonResolver;
import org.jfrog.hudson.pipeline.declarative.BuildDataFile;
import org.jfrog.hudson.pipeline.declarative.utils.DeclarativePipelineUtils;
import org.jfrog.hudson.util.BuildUniqueIdentifierHelper;
import org.jfrog.hudson.util.JenkinsBuildInfoLog;
import org.jfrog.hudson.util.SerializationUtils;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;

/**
 * Created by Bar Belity on 19/11/2020.
 */
abstract public class NpmInstallCiStepBase extends AbstractStepImpl {

    private final NpmBuild npmBuild;
    private String customBuildNumber;
    private String customBuildName;
    private String project;
    private String resolverId;
    private String javaArgs; // Added to allow java remote debugging
    private String path;
    private String args;
    private String module;
    protected boolean ciCommand;

    public NpmInstallCiStepBase() {
        this.npmBuild = new NpmBuild();
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
    public void setPath(String path) {
        this.path = path;
    }

    @DataBoundSetter
    public void setModule(String module) {
        this.module = module;
    }

    @DataBoundSetter
    public void setArgs(String args) {
        this.args = args;
    }

    @DataBoundSetter
    public void setTool(String tool) {
        npmBuild.setTool(tool);
    }

    public abstract String getUsageReportFeatureName();

    public static class Execution extends ArtifactorySynchronousNonBlockingStepExecution<Void> {

        private transient NpmInstallCiStepBase step;

        @Inject
        public Execution(NpmInstallCiStepBase step, StepContext context) throws IOException, InterruptedException {
            super(context);
            this.step = step;
        }

        @Override
        protected Void runStep() throws Exception {
            BuildInfo buildInfo = DeclarativePipelineUtils.getBuildInfo(rootWs, build, step.customBuildName, step.customBuildNumber, step.project);
            CommonResolver resolver = getResolver(BuildUniqueIdentifierHelper.getBuildNumber(build));
            step.npmBuild.setResolver(resolver);
            Utils.addNpmToPath(ws, listener, env, launcher, step.npmBuild.getTool());
            NpmInstallCiExecutor npmInstallCiExecutor = new NpmInstallCiExecutor(buildInfo, launcher, step.npmBuild, step.javaArgs, step.args, ws, step.path, step.module, env, listener, build, step.ciCommand);
            npmInstallCiExecutor.execute();
            DeclarativePipelineUtils.saveBuildInfo(npmInstallCiExecutor.getBuildInfo(), rootWs, build, new JenkinsBuildInfoLog(listener));
            return null;
        }

        @Override
        public String getUsageReportFeatureName() {
            return step.getUsageReportFeatureName();
        }

        private CommonResolver getResolver(String buildNumber) throws IOException, InterruptedException {
            if (StringUtils.isBlank(step.resolverId)) {
                return null;
            }
            BuildDataFile buildDataFile = DeclarativePipelineUtils.readBuildDataFile(rootWs, buildNumber, NpmResolverStep.STEP_NAME, step.resolverId);
            if (buildDataFile == null) {
                throw new IOException("Resolver " + step.resolverId + " doesn't exist!");
            }
            CommonResolver resolver = SerializationUtils.createMapper().treeToValue(buildDataFile.get(NpmResolverStep.STEP_NAME), CommonResolver.class);
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

        @Override
        public org.jfrog.hudson.ArtifactoryServer getUsageReportServer() throws IOException, InterruptedException {
            CommonResolver resolver = getResolver(BuildUniqueIdentifierHelper.getBuildNumber(build));
            if (resolver != null) {
                return resolver.getArtifactoryServer();
            }
            return null;
        }
    }
}
