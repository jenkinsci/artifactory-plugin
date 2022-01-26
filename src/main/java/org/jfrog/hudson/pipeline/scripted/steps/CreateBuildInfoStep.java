package org.jfrog.hudson.pipeline.scripted.steps;

import com.google.inject.Inject;
import hudson.Extension;
import org.jenkinsci.plugins.workflow.steps.*;
import org.jfrog.hudson.ArtifactoryServer;
import org.jfrog.hudson.pipeline.ArtifactorySynchronousStepExecution;
import org.jfrog.hudson.pipeline.common.types.buildInfo.BuildInfo;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;

/**
 * Created by romang on 5/2/16.
 */
public class CreateBuildInfoStep extends AbstractStepImpl {
    static final String STEP_NAME = "newBuildInfo";
    @DataBoundConstructor
    public CreateBuildInfoStep() {
    }

    public static class Execution extends ArtifactorySynchronousStepExecution<BuildInfo> {

        private transient CreateBuildInfoStep step;

        @Inject
        public Execution(CreateBuildInfoStep step, StepContext context) throws IOException, InterruptedException {
            super(context);
            this.step = step;
        }

        @Override
        protected BuildInfo runStep() throws Exception {
            return new BuildInfo(build);
        }

        @Override
        public ArtifactoryServer getUsageReportServer() {
            return null;
        }

        @Override
        public String getUsageReportFeatureName() {
            return null;
        }
    }

    @Extension
    public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(CreateBuildInfoStep.Execution.class);
        }

        @Override
        public String getFunctionName() {
            return STEP_NAME;
        }

        @Override
        public String getDisplayName() {
            return "New buildInfo";
        }

        @Override
        public boolean isAdvanced() {
            return true;
        }
    }

}

