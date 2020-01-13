package org.jfrog.hudson.pipeline.declarative.steps.go;

import hudson.Extension;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jfrog.hudson.pipeline.common.types.deployers.NpmGoDeployer;
import org.jfrog.hudson.pipeline.declarative.steps.common.NpmGoDeployerResolver;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.List;

public class GoDeployerStep extends NpmGoDeployerResolver {

    static final String STEP_NAME = "rtGoDeployer";
    private NpmGoDeployer goDeployer;

    @DataBoundConstructor
    public GoDeployerStep(String id, String serverId, String repo) {
        super(STEP_NAME, id, serverId);
        goDeployer = new NpmGoDeployer();
        goDeployer.setRepo(repo);
        buildDataFile.putPOJO(goDeployer);
    }

    @DataBoundSetter
    public void setCustomBuildName(String customBuildName) {
        goDeployer.setCustomBuildName(customBuildName);
    }

    @DataBoundSetter
    public void setProperties(List<String> properties) {
        buildDataFile.put("properties", String.join(";", properties));
    }

    @Extension
    public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(Execution.class);
        }

        @Override
        public String getFunctionName() {
            return STEP_NAME;
        }

        @Override
        public String getDisplayName() {
            return "set go deployer";
        }

        @Override
        public boolean isAdvanced() {
            return true;
        }
    }
}
