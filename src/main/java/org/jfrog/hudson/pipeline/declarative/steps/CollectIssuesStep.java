package org.jfrog.hudson.pipeline.declarative.steps;

import com.google.inject.Inject;
import hudson.Extension;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jfrog.hudson.ArtifactoryServer;
import org.jfrog.hudson.SpecConfiguration;
import org.jfrog.hudson.pipeline.ArtifactorySynchronousStepExecution;
import org.jfrog.hudson.pipeline.common.Utils;
import org.jfrog.hudson.pipeline.common.executors.CollectIssuesExecutor;
import org.jfrog.hudson.pipeline.common.types.buildInfo.BuildInfo;
import org.jfrog.hudson.pipeline.declarative.utils.DeclarativePipelineUtils;
import org.jfrog.hudson.util.JenkinsBuildInfoLog;
import org.jfrog.hudson.util.SpecUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;

@SuppressWarnings("unused")
public class CollectIssuesStep extends AbstractStepImpl {
    public static final String STEP_NAME = "rtCollectIssues";
    private final String serverId;
    private String config;
    private String configPath;
    private String customBuildNumber;
    private String customBuildName;
    private String project;

    @DataBoundConstructor
    public CollectIssuesStep(String serverId) {
        this.serverId = serverId;
    }

    @DataBoundSetter
    public void setConfig(String config) {
        this.config = config;
    }

    @DataBoundSetter
    public void setConfigPath(String configPath) {
        this.configPath = configPath;
    }

    @DataBoundSetter
    public void setBuildName(String buildName) {
        this.customBuildName = buildName;
    }

    @DataBoundSetter
    public void setBuildNumber(String buildNumber) {
        this.customBuildNumber = buildNumber;
    }

    @DataBoundSetter
    public void setProject(String project) {
        this.project = project;
    }

    public static class Execution extends ArtifactorySynchronousStepExecution<Void> {

        private transient final CollectIssuesStep step;

        @Inject
        public Execution(CollectIssuesStep step, StepContext context) throws IOException, InterruptedException {
            super(context);
            this.step = step;
        }

        @Override
        protected Void runStep() throws Exception {
            // Set spec
            SpecConfiguration specConfiguration = new SpecConfiguration(step.config, step.configPath);
            String config = SpecUtils.getSpecStringFromSpecConf(specConfiguration, env, ws, listener.getLogger());

            // Get build info
            BuildInfo buildInfo = DeclarativePipelineUtils.getBuildInfo(rootWs, build, step.customBuildName, step.customBuildNumber, step.project);

            // Get server
            org.jfrog.hudson.pipeline.common.types.ArtifactoryServer pipelineServer = DeclarativePipelineUtils.getArtifactoryServer(build, rootWs, step.serverId, true);

            // Collect issues
            CollectIssuesExecutor collectIssuesExecutor = new CollectIssuesExecutor(build, listener, ws, buildInfo.getName(), config, buildInfo.getIssues(), pipelineServer, buildInfo.getProject());
            collectIssuesExecutor.execute();

            DeclarativePipelineUtils.saveBuildInfo(buildInfo, rootWs, build, new JenkinsBuildInfoLog(listener));
            return null;
        }

        @Override
        public ArtifactoryServer getUsageReportServer() throws Exception {
            org.jfrog.hudson.pipeline.common.types.ArtifactoryServer server = DeclarativePipelineUtils.getArtifactoryServer(build, rootWs, step.serverId, true);
            return Utils.prepareArtifactoryServer(null, server);
        }

        @Override
        public String getUsageReportFeatureName() {
            return STEP_NAME;
        }
    }

    @Extension
    public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(CollectIssuesStep.Execution.class);
        }

        @Override
        public String getFunctionName() {
            return STEP_NAME;
        }

        @Override
        public String getDisplayName() {
            return "Collect issues";
        }

        @Override
        public boolean isAdvanced() {
            return true;
        }
    }
}
