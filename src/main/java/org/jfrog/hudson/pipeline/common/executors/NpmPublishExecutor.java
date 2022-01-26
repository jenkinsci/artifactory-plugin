package org.jfrog.hudson.pipeline.common.executors;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jfrog.hudson.pipeline.common.types.buildInfo.BuildInfo;
import org.jfrog.hudson.pipeline.common.types.builds.NpmBuild;
import org.jfrog.hudson.pipeline.common.types.deployers.CommonDeployer;
import org.jfrog.hudson.pipeline.common.types.resolvers.CommonResolver;

/**
 * Created by Yahav Itzhak on 25 Nov 2018.
 */
public class NpmPublishExecutor extends NpmExecutor {

    public NpmPublishExecutor(TaskListener listener, BuildInfo buildInfo, Launcher launcher, NpmBuild npmBuild, String javaArgs, String path, String module, FilePath ws, EnvVars env, Run build) {
        super(buildInfo, launcher, npmBuild, javaArgs, ws, path, module, env, listener, build);
    }

    @Override
    public void execute() throws Exception {
        CommonDeployer deployer = (CommonDeployer) npmBuild.getDeployer();
        if (deployer.isEmpty()) {
            throw new IllegalStateException("Deployer must be configured with deployment repository and Artifactory server");
        }
        super.execute(deployer, new CommonResolver(), "", false, "NpmPublish");
    }
}
