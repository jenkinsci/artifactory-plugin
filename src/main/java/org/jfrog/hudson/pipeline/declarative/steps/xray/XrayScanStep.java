package org.jfrog.hudson.pipeline.declarative.steps.xray;

import com.google.inject.Inject;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.jfrog.hudson.pipeline.common.executors.XrayExecutor;
import org.jfrog.hudson.pipeline.common.types.ArtifactoryServer;
import org.jfrog.hudson.pipeline.common.types.XrayScanConfig;
import org.jfrog.hudson.pipeline.declarative.utils.DeclarativePipelineUtils;
import org.jfrog.hudson.util.BuildUniqueIdentifierHelper;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * @author Alexei Vainshtein
 */
@SuppressWarnings("unused")
public class XrayScanStep extends AbstractStepImpl {

    public static final String STEP_NAME = "xrayScan";
    private XrayScanConfig xrayScanConfig;
    private String serverId;

    @DataBoundConstructor
    public XrayScanStep(String serverId) {
        this.serverId = serverId;
        this.xrayScanConfig = new XrayScanConfig();
    }

    @DataBoundSetter
    public void setBuildName(String buildName) {
        xrayScanConfig.setBuildName(buildName);
    }

    @DataBoundSetter
    public void setBuildNumber(String buildNumber) {
        xrayScanConfig.setBuildNumber(buildNumber);
    }

    @DataBoundSetter
    public void setFailBuild(boolean failBuild) {
        xrayScanConfig.setFailBuild(failBuild);
    }

    private XrayScanConfig prepareXrayScanConfig(Run build) {
        if (StringUtils.isBlank(xrayScanConfig.getBuildName())) {
            xrayScanConfig.setBuildName(BuildUniqueIdentifierHelper.getBuildName(build));
        }
        if (StringUtils.isBlank(xrayScanConfig.getBuildNumber())) {
            xrayScanConfig.setBuildNumber(BuildUniqueIdentifierHelper.getBuildNumber(build));
        }
        return xrayScanConfig;
    }

    public static class Execution extends AbstractSynchronousNonBlockingStepExecution<Void> {
        private static final long serialVersionUID = 1L;

        @Inject(optional = true)
        private transient XrayScanStep step;

        @StepContextParameter
        private transient Run build;

        @StepContextParameter
        private transient TaskListener listener;

        @StepContextParameter
        private transient FilePath ws;

        @Override
        protected Void run() throws Exception {
            XrayScanConfig xrayScanConfig = step.prepareXrayScanConfig(build);
            ArtifactoryServer server = DeclarativePipelineUtils.getArtifactoryServer(build, ws, getContext(), step.serverId);
            XrayExecutor xrayExecutor = new XrayExecutor(xrayScanConfig, listener, server, build);
            xrayExecutor.execute();
            return null;
        }
    }

    @Extension
    public static final class DescriptorImpl extends AbstractStepDescriptorImpl {
        public DescriptorImpl() {
            super(XrayScanStep.Execution.class);
        }

        @Override
        public String getFunctionName() {
            return STEP_NAME;
        }

        @Override
        public String getDisplayName() {
            return "run Xray scan";
        }

        @Override
        public boolean isAdvanced() {
            return true;
        }
    }
}
