package org.jfrog.hudson.pipeline.steps;

import com.google.inject.Inject;
import hudson.Extension;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jfrog.hudson.pipeline.types.PipelineEnv;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Created by romang on 5/2/16.
 */
public class CollectEnvStep extends AbstractStepImpl {

    private PipelineEnv env;

    @DataBoundConstructor
    public CollectEnvStep(PipelineEnv env) {
        this.env = env;
    }

    public PipelineEnv getEnv() {
        return env;
    }

    public static class Execution extends AbstractSynchronousStepExecution<Boolean> {
        private static final long serialVersionUID = 1L;

        @Inject(optional = true)
        private transient CollectEnvStep step;

        @Override
        protected Boolean run() throws Exception {
            step.getEnv().collectVariables(getContext());
            return true;
        }
    }

    @Extension
    public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(CollectEnvStep.Execution.class);
        }

        @Override
        public String getFunctionName() {
            return "collectEnv";
        }

        @Override
        public String getDisplayName() {
            return "Collect environment variables and system properties";
        }
    }

}

