package org.jfrog.hudson.pipeline.declarative.steps.generic;

import com.google.inject.Inject;
import hudson.Extension;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;

import static org.jfrog.build.extractor.clientConfiguration.util.EditPropertiesHelper.EditPropertiesActionType;

@SuppressWarnings("unused")
public class SetPropsStep extends EditPropsStep {
    static final String STEP_NAME = "rtSetProps";

    @DataBoundConstructor
    public SetPropsStep(String serverId) {
        super(serverId, EditPropertiesActionType.SET);
    }

    public static class Execution extends EditPropsStep.Execution {

        @Inject
        public Execution(EditPropsStep step, StepContext context) throws IOException, InterruptedException {
            super(step, context);
        }

        @Override
        protected Void runStep() throws Exception {
            super.editPropsRun();
            return null;
        }

        @Override
        public String getUsageReportFeatureName() {
            return STEP_NAME;
        }
    }

    @Extension
    public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(SetPropsStep.Execution.class);
        }

        @Override
        public String getFunctionName() {
            return STEP_NAME;
        }

        @Override
        public String getDisplayName() {
            return "Set properties";
        }

        @Override
        public boolean isAdvanced() {
            return true;
        }
    }
}
