package org.jfrog.hudson.pipeline.steps;

import com.google.inject.Inject;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.*;
import org.jfrog.hudson.pipeline.PipelineUtils;
import org.jfrog.hudson.pipeline.executors.GenericDownloadExecutor;
import org.jfrog.hudson.pipeline.types.BuildInfo;
import org.jfrog.hudson.pipeline.types.PipelineBuildInfoAccessor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.jfrog.hudson.pipeline.types.ArtifactoryServer;

import java.util.HashMap;
import java.util.Map;

public class DownloadStep extends AbstractStepImpl {

    private BuildInfo buildInfo;
    private String json;
    private ArtifactoryServer server;

    @DataBoundConstructor
    public DownloadStep(String json, BuildInfo providedBuildInfo, ArtifactoryServer server) {
        this.json = json;
        this.buildInfo = providedBuildInfo;
        this.server = server;
    }

    public BuildInfo getBuildInfo() {
        return buildInfo;
    }

    public String getJson() {
        return json;
    }

    public ArtifactoryServer getServer() {
        return server;
    }

    public static class Execution extends AbstractSynchronousStepExecution<BuildInfo> {
        private static final long serialVersionUID = 1L;
        @StepContextParameter
        private transient FilePath ws;

        @StepContextParameter
        private transient Run build;

        @StepContextParameter
        private transient TaskListener listener;

        @Inject(optional = true)
        private transient DownloadStep step;

        @Override
        protected BuildInfo run() throws Exception {
            BuildInfo build = new GenericDownloadExecutor(PipelineUtils.prepareArtifactoryServer(null, step.getServer()), this.listener, this.build, this.ws, step.getBuildInfo()).execution(step.getJson());
            new PipelineBuildInfoAccessor(build).captureVariables(getContext());
            return build;
        }
    }

    @Extension
    public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(DownloadStep.Execution.class);
        }

        @Override
        // The step is invoked by ArtifactoryServer by the step name
        public String getFunctionName() {
            return "artifactoryDownload";
        }

        @Override
        public String getDisplayName() {
            return "Download artifacts";
        }

        @Override
        public Map<String, Object> defineArguments(Step step) throws UnsupportedOperationException {
            return new HashMap<String, Object>();
        }
    }

}
