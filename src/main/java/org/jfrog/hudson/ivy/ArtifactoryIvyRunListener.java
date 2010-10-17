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
import hudson.ivy.IvyModuleSetBuild;
import hudson.model.AbstractBuild;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import org.jfrog.hudson.BuildInfoResultAction;


/**
 * @author Tomer Cohen
 */
@Extension(optional = true)
public class ArtifactoryIvyRunListener extends RunListener<AbstractBuild> {
    public ArtifactoryIvyRunListener() {
        super(AbstractBuild.class);
    }

    @Override
    public void onCompleted(AbstractBuild run, TaskListener listener) {
        if (run instanceof IvyModuleSetBuild) {
            IvyModuleSetBuild ivyRun = (IvyModuleSetBuild) run;
            Result result = ivyRun.getResult();
            if (result == null || result.isWorseThan(Result.SUCCESS)) {
                return;
            }
            ArtifactoryIvyConfigurator artifactoryIvyConfigurator =
                    ivyRun.getProject().getBuildWrappersList().get(ArtifactoryIvyConfigurator.class);
            if (artifactoryIvyConfigurator == null) {
                return;
            }
            if (artifactoryIvyConfigurator.isDeployBuildInfo()) {
                run.getActions().add(new BuildInfoResultAction(artifactoryIvyConfigurator.getArtifactoryName(), run));
            }
        }
    }
}


