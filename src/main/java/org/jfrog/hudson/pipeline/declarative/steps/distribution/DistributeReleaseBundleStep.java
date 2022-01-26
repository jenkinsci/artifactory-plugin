package org.jfrog.hudson.pipeline.declarative.steps.distribution;

import com.google.inject.Inject;
import hudson.Extension;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jfrog.hudson.pipeline.ArtifactorySynchronousStepExecution;
import org.jfrog.hudson.pipeline.common.executors.ReleaseBundleDistributeExecutor;
import org.jfrog.hudson.pipeline.common.types.DistributionServer;
import org.jfrog.hudson.pipeline.declarative.utils.DeclarativePipelineUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.List;

/**
 * @author yahavi
 **/
public class DistributeReleaseBundleStep extends RemoteReleaseBundleStep {
    public static final String STEP_NAME = "dsDistributeReleaseBundle";

    @DataBoundConstructor
    public DistributeReleaseBundleStep(String serverId, String name, String version) {
        super(serverId, name, version);
    }

    @DataBoundSetter
    public void setCountryCodes(List<String> countryCodes) {
        this.countryCodes = countryCodes;
    }

    @DataBoundSetter
    public void setDistRules(String distRules) {
        this.distRules = distRules;
    }

    @DataBoundSetter
    public void setSiteName(String siteName) {
        this.siteName = siteName;
    }

    @DataBoundSetter
    public void setCityName(String cityName) {
        this.cityName = cityName;
    }

    @DataBoundSetter
    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    @DataBoundSetter
    public void setSync(boolean sync) {
        this.sync = sync;
    }

    public static class Execution extends ArtifactorySynchronousStepExecution<Void> {

        private final transient DistributeReleaseBundleStep step;

        @Inject
        public Execution(DistributeReleaseBundleStep step, StepContext context) throws IOException, InterruptedException {
            super(context);
            this.step = step;
        }

        @SuppressWarnings("ConstantConditions")
        @Override
        protected Void runStep() throws Exception {
            DistributionServer server = DeclarativePipelineUtils.getDistributionServer(build, rootWs, step.serverId, true);
            new ReleaseBundleDistributeExecutor(server, step.name, step.version, step.dryRun, step.sync, step.distRules,
                    step.countryCodes, step.siteName, step.cityName, listener, build, ws).execute();
            return null;
        }

        @Override
        public org.jfrog.hudson.ArtifactoryServer getUsageReportServer() throws Exception {
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
            super(DistributeReleaseBundleStep.Execution.class);
        }

        @Override
        public String getFunctionName() {
            return STEP_NAME;
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Distribute a release bundle";
        }

        @Override
        public boolean isAdvanced() {
            return true;
        }
    }
}
