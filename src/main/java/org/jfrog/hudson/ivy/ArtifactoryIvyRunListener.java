package org.jfrog.hudson.ivy;

import hudson.Extension;
import hudson.ivy.IvyModuleSet;
import hudson.ivy.IvyModuleSetBuild;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import org.jfrog.hudson.ArtifactoryRedeployPublisher;
import org.jfrog.hudson.BuildInfoResultAction;


/**
 * @author Tomer Cohen
 */
@Extension
public class ArtifactoryIvyRunListener extends RunListener<IvyModuleSetBuild> {
    public ArtifactoryIvyRunListener() {
        super(IvyModuleSetBuild.class);
    }

    @Override
    public void onCompleted(IvyModuleSetBuild run, TaskListener listener) {
        Result result = run.getResult();
        if (result == null) {
            return;
        }
        ArtifactoryIvyConfigurator artifactoryIvyConfigurator =
                ((IvyModuleSet) run.getProject()).getBuildWrappersList().get(ArtifactoryIvyConfigurator.class);
        ArtifactoryRedeployPublisher publisher =
                new ArtifactoryRedeployPublisher(artifactoryIvyConfigurator.getDetails(), true,
                        artifactoryIvyConfigurator.getUsername(), artifactoryIvyConfigurator.getPassword(), true);
        if (result.isBetterOrEqualTo(Result.SUCCESS)) {
            run.getActions().add(new BuildInfoResultAction(publisher, run));
        }
    }
}


