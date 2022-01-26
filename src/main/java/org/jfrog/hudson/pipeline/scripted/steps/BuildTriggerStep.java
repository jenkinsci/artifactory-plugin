package org.jfrog.hudson.pipeline.scripted.steps;

import com.google.inject.Inject;
import hudson.Extension;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jfrog.hudson.pipeline.ArtifactorySynchronousNonBlockingStepExecution;
import org.jfrog.hudson.pipeline.common.Utils;
import org.jfrog.hudson.pipeline.common.executors.BuildTriggerExecutor;
import org.jfrog.hudson.pipeline.common.types.ArtifactoryServer;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;

/**
 * @author yahavi
 */
@SuppressWarnings("unused")
public class BuildTriggerStep extends AbstractStepImpl {
    static final String STEP_NAME = "artifactoryBuildTrigger";
    private final ArtifactoryServer server;
    private final String paths;
    private final String spec;

    @DataBoundConstructor
    public BuildTriggerStep(ArtifactoryServer server, String paths, String spec) {
        this.server = server;
        this.paths = paths;
        this.spec = spec;
    }

    public static class Execution extends ArtifactorySynchronousNonBlockingStepExecution<Void> {

        private transient final BuildTriggerStep step;

        @Inject
        public Execution(BuildTriggerStep step, StepContext context) throws IOException, InterruptedException {
            super(context);
            this.step = step;
        }

        @Override
        protected Void runStep() throws Exception {
            new BuildTriggerExecutor(build, listener, step.server, step.paths, step.spec).execute();
            return null;
        }

        @Override
        public org.jfrog.hudson.ArtifactoryServer getUsageReportServer() {
            return Utils.prepareArtifactoryServer(null, step.server);
        }

        @Override
        public String getUsageReportFeatureName() {
            return STEP_NAME;
        }
    }

    @Extension
    public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(BuildTriggerStep.Execution.class);
        }

        @Override
        public String getFunctionName() {
            return STEP_NAME;
        }

        @Override
        public String getDisplayName() {
            return "Trigger Artifactory build";
        }

        @Override
        public boolean isAdvanced() {
            return true;
        }
    }
}
