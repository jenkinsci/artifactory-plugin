package org.jfrog.hudson.pipeline.declarative.steps.gradle;

import com.google.inject.Inject;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jfrog.hudson.ArtifactoryServer;
import org.jfrog.hudson.pipeline.ArtifactorySynchronousNonBlockingStepExecution;
import org.jfrog.hudson.pipeline.declarative.BuildDataFile;
import org.jfrog.hudson.util.BuildUniqueIdentifierHelper;
import org.jfrog.hudson.util.JenkinsBuildInfoLog;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;

import static org.jfrog.hudson.pipeline.declarative.utils.DeclarativePipelineUtils.writeBuildDataFile;

/**
 * Base class for Gradle deployer and resolver.
 */
@SuppressWarnings("unused")
public class GradleDeployerResolver extends AbstractStepImpl {

    BuildDataFile buildDataFile;

    @DataBoundConstructor
    public GradleDeployerResolver(String stepName, String stepId, String serverId) {
        buildDataFile = new BuildDataFile(stepName, stepId).put("serverId", serverId);
    }

    @DataBoundSetter
    public void setRepo(String repo) {
        buildDataFile.put("repo", repo);
    }

    public static class Execution extends ArtifactorySynchronousNonBlockingStepExecution<Void> {

        private transient final GradleDeployerResolver step;

        @Inject
        public Execution(GradleDeployerResolver step, StepContext context) throws IOException, InterruptedException {
            super(context);
            this.step = step;
        }

        @Override
        protected Void runStep() throws Exception {
            String buildNumber = BuildUniqueIdentifierHelper.getBuildNumber(build);
            BuildDataFile buildDataFile = step.buildDataFile;
            writeBuildDataFile(rootWs, buildNumber, buildDataFile, new JenkinsBuildInfoLog(listener));
            return null;
        }

        @Override
        public ArtifactoryServer getUsageReportServer() throws IOException, InterruptedException {
            return null;
        }

        @Override
        public String getUsageReportFeatureName() {
            return null;
        }
    }
}
