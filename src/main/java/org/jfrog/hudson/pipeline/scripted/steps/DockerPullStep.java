package org.jfrog.hudson.pipeline.scripted.steps;

import com.google.inject.Inject;
import hudson.AbortException;
import hudson.Extension;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jfrog.build.extractor.docker.DockerUtils;
import org.jfrog.hudson.pipeline.ArtifactorySynchronousNonBlockingStepExecution;
import org.jfrog.hudson.pipeline.common.Utils;
import org.jfrog.hudson.pipeline.common.executors.DockerPullExecutor;
import org.jfrog.hudson.pipeline.common.types.ArtifactoryServer;
import org.jfrog.hudson.pipeline.common.types.buildInfo.BuildInfo;
import org.jfrog.hudson.util.JenkinsBuildInfoLog;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;

/**
 * Created by romang on 5/2/16.
 */
public class DockerPullStep extends AbstractStepImpl {
    static final String STEP_NAME = "dockerPullStep";
    private final String image;
    private final ArtifactoryServer server;
    private final BuildInfo buildInfo;
    private final String host;
    private final String javaArgs;
    private String sourceRepo;

    @DataBoundConstructor
    public DockerPullStep(String image, String host, String sourceRepo, String javaArgs, BuildInfo buildInfo, ArtifactoryServer server) {
        this.image = image;
        this.host = host;
        this.buildInfo = buildInfo;
        this.server = server;
        this.javaArgs = javaArgs;
        this.sourceRepo = sourceRepo;
    }

    public BuildInfo getBuildInfo() {
        return buildInfo;
    }

    public String getImage() {
        return image;
    }

    public ArtifactoryServer getServer() {
        return server;
    }

    public String getSourceRepo() {
        return sourceRepo;
    }

    public String getHost() {
        return host;
    }

    public String getJavaArgs() {
        return javaArgs;
    }

    public static class Execution extends ArtifactorySynchronousNonBlockingStepExecution<BuildInfo> {

        private final transient DockerPullStep step;

        @Inject
        public Execution(DockerPullStep step, StepContext context) throws IOException, InterruptedException {
            super(context);
            this.step = step;
        }

        @Override
        protected BuildInfo runStep() throws Exception {
            if (step.getImage() == null) {
                getContext().onFailure(new AbortException("Missing 'image' parameter"));
                return null;
            }
            if (step.getSourceRepo() == null) {
                getContext().onFailure(new AbortException("Missing 'sourceRepo' parameter"));
                return null;
            }
            BuildInfo buildInfo = Utils.prepareBuildinfo(build, step.getBuildInfo());
            String imageTag = step.getImage();
            if (!DockerUtils.isImageVersioned(imageTag)) {
                imageTag += ":latest";
            }
            DockerPullExecutor dockerExecutor = new DockerPullExecutor(step.getServer(), buildInfo, build, step.image, step.sourceRepo, step.host, step.javaArgs, launcher, listener, ws, env);
            dockerExecutor.execute();
            JenkinsBuildInfoLog log = new JenkinsBuildInfoLog(listener);
            log.info("Successfully pulled docker image: " + imageTag);
            return dockerExecutor.getBuildInfo();
        }

        @Override
        public org.jfrog.hudson.ArtifactoryServer getUsageReportServer() {
            return Utils.prepareArtifactoryServer(null, step.getServer());
        }

        @Override
        public String getUsageReportFeatureName() {
            return STEP_NAME;
        }
    }

    @Extension
    public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(DockerPullStep.Execution.class);
        }

        @Override
        public String getFunctionName() {
            return STEP_NAME;
        }

        @Override
        public String getDisplayName() {
            return "Artifactory docker pull";
        }

        @Override
        public boolean isAdvanced() {
            return true;
        }
    }
}

