/*
 * Copyright (C) 2011 JFrog Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jfrog.hudson.release.scm.git;

import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Result;
import org.jfrog.hudson.release.ReleaseAction;
import org.jfrog.hudson.release.scm.AbstractScmCoordinator;

import java.io.IOException;

/**
 * Git scm coordinator. Interacts with the {@link GitManager} to fulfill the release process.
 *
 * @author Yossi Shaul
 */
public class GitCoordinator extends AbstractScmCoordinator {
    private GitManager scmManager;

    public GitCoordinator(AbstractBuild build, BuildListener listener) {
        super(build, listener);
    }

    public void prepare() throws IOException, InterruptedException {
        scmManager = new GitManager(build, listener);
        // TODO: remove prepare method from ScmManager
        scmManager.prepare();
    }

    public void afterSuccessfulReleaseVersionBuild() throws InterruptedException, IOException {
        // commit local changes
        scmManager.commitWorkingCopy("Committing release version");

        ReleaseAction releaseAction = build.getAction(ReleaseAction.class);
        if (releaseAction.isCreateVcsTag()) {
            scmManager.createTag(releaseAction.getTagUrl(), releaseAction.getTagComment());
            releaseAction.setTagCreated(true);
        }
    }

    public void afterDevelopmentVersionChange() throws IOException, InterruptedException {
        scmManager.commitWorkingCopy("Committing next development version");
    }

    public void buildCompleted() throws IOException, InterruptedException {
        GitManager gitManager = (GitManager) scmManager;
        if (build.getResult().isBetterOrEqualTo(Result.SUCCESS)) {
            // push changes done by the release process (but don't push tags created by the git scm)
            gitManager.push();
            ReleaseAction releaseAction = build.getAction(ReleaseAction.class);
            gitManager.pushTag(releaseAction.getTagUrl());
        } else {
            scmManager.safeRevertWorkingCopy();
        }
    }

    public String getRemoteUrlForPom() {
        return null;
    }
}
