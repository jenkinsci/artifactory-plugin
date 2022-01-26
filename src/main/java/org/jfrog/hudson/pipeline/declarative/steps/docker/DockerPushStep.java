package org.jfrog.hudson.pipeline.declarative.steps.docker;

import com.google.common.collect.ArrayListMultimap;
import com.google.inject.Inject;
import hudson.Extension;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jfrog.hudson.ArtifactoryServer;
import org.jfrog.hudson.pipeline.ArtifactorySynchronousNonBlockingStepExecution;
import org.jfrog.hudson.pipeline.common.Utils;
import org.jfrog.hudson.pipeline.common.executors.DockerPushExecutor;
import org.jfrog.hudson.pipeline.common.types.buildInfo.BuildInfo;
import org.jfrog.hudson.pipeline.declarative.utils.DeclarativePipelineUtils;
import org.jfrog.hudson.util.JenkinsBuildInfoLog;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;

/**
 * @author Alexei Vainshtein
 */

@SuppressWarnings("unused")
public class DockerPushStep extends AbstractStepImpl {
    static final String STEP_NAME = "rtDockerPush";
    private final ArrayListMultimap<String, String> properties = ArrayListMultimap.create();
    private final String serverId;
    private final String image;
    private final String targetRepo;
    private String host;
    private String buildNumber;
    private String buildName;
    private String project;
    private String javaArgs;

    @DataBoundConstructor
    public DockerPushStep(String serverId, String image, String targetRepo, String javaArgs) {
        this.serverId = serverId;
        this.image = image;
        this.targetRepo = targetRepo;
        this.javaArgs = javaArgs;
    }

    @DataBoundSetter
    public void setHost(String host) {
        this.host = host;
    }

    @DataBoundSetter
    public void setBuildNumber(String buildNumber) {
        this.buildNumber = buildNumber;
    }

    @DataBoundSetter
    public void setBuildName(String buildName) {
        this.buildName = buildName;
    }

    @DataBoundSetter
    public void setProject(String project) {
        this.project = project;
    }

    @DataBoundSetter
    public void setJavaArgs(String javaArgs) {
        this.javaArgs = javaArgs;
    }

    @DataBoundSetter
    public void setProperties(String properties) {
        String[] props = properties.split(";");
        // Extracts the key,value property
        for (String property : props) {
            String[] keyValue = property.split("=");
            if (keyValue.length == 2) {
                this.properties.put(keyValue[0], keyValue[1]);
            }
        }
    }

    public static class Execution extends ArtifactorySynchronousNonBlockingStepExecution<Void> {

        private transient final DockerPushStep step;

        @Inject
        public Execution(DockerPushStep step, StepContext context) throws IOException, InterruptedException {
            super(context);
            this.step = step;
        }

        @Override
        protected Void runStep() throws Exception {
            BuildInfo buildInfo = DeclarativePipelineUtils.getBuildInfo(rootWs, build, step.buildName, step.buildNumber, step.project);
            org.jfrog.hudson.pipeline.common.types.ArtifactoryServer pipelineServer = DeclarativePipelineUtils.getArtifactoryServer(build, rootWs, step.serverId, true);
            DockerPushExecutor dockerExecutor = new DockerPushExecutor(pipelineServer, buildInfo, build, step.image, step.targetRepo, step.host, step.javaArgs, launcher, step.properties, listener, ws, env);
            dockerExecutor.execute();
            DeclarativePipelineUtils.saveBuildInfo(dockerExecutor.getBuildInfo(), rootWs, build, new JenkinsBuildInfoLog(listener));
            return null;
        }

        @Override
        public ArtifactoryServer getUsageReportServer() {
            return Utils.prepareArtifactoryServer(step.serverId, null);
        }

        @Override
        public String getUsageReportFeatureName() {
            return STEP_NAME;
        }
    }

    @Extension
    public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(DockerPushStep.Execution.class);
        }

        @Override
        public String getFunctionName() {
            return STEP_NAME;
        }

        @Override
        public String getDisplayName() {
            return "run Artifactory docker push";
        }

        @Override
        public boolean isAdvanced() {
            return true;
        }
    }
}
