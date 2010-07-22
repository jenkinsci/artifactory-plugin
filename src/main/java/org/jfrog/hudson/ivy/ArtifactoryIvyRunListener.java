/*
 * Copyright (C) 2010 JFrog Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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


