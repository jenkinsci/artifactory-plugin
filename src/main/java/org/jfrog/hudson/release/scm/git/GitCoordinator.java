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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Git scm coordinator. Interacts with the {@link GitManager} to fulfill the release process.
 *
 * @author Yossi Shaul
 */
public class GitCoordinator extends AbstractScmCoordinator {
    private static Logger debuggingLogger = Logger.getLogger(GitCoordinator.class.getName());

    private GitManager scmManager;
    private String releaseBranch;
    private String checkoutBranch = "master";

    public GitCoordinator(AbstractBuild build, BuildListener listener) {
        super(build, listener);
        // TODO: check if should create release branch
        ReleaseAction releaseAction = build.getAction(ReleaseAction.class);
        releaseBranch = releaseAction.getReleaseBranch();
    }

    public void prepare() throws IOException, InterruptedException {
        scmManager = new GitManager(build, listener);

        scmManager.getCurrentCommitHash();

        // create a new branch and start it
        scmManager.checkoutBranch(releaseBranch, true);
    }

    public void afterSuccessfulReleaseVersionBuild() throws InterruptedException, IOException {
        // commit local changes
        scmManager.commitWorkingCopy("Committing release version");

        ReleaseAction releaseAction = build.getAction(ReleaseAction.class);
        if (releaseAction.isCreateVcsTag()) {
            scmManager.createTag(releaseAction.getTagUrl(), releaseAction.getTagComment());
        }

        // TODO: beforeDevelopmentVersionChange should do this
        // done working on the release branch, checkout back to master
        // TODO: discover master branch
        scmManager.checkoutBranch(checkoutBranch, false);

    }

    public void afterDevelopmentVersionChange() throws IOException, InterruptedException {
        scmManager.commitWorkingCopy("Committing next development version");
    }

    public void buildCompleted() throws IOException, InterruptedException {
        if (build.getResult().isBetterOrEqualTo(Result.SUCCESS)) {
            // push the release branch
            scmManager.push(scmManager.getRemoteUrl(), releaseBranch);
            // push the tag if created
            ReleaseAction releaseAction = build.getAction(ReleaseAction.class);
            if (releaseAction.isCreateVcsTag()) {
                scmManager.pushTag(scmManager.getRemoteUrl(), releaseAction.getTagUrl());
            }
            // TODO: only if different from the original
            // push the next development version (if different from original)
            // pull before attempting to push changes
            //scmManager.pull(scmManager.getRemoteUrl(), checkoutBranch);
            scmManager.push(scmManager.getRemoteUrl(), checkoutBranch);
        } else {
            // go back to the original checkout branch
            scmManager.checkoutBranch(checkoutBranch, false);
            // delete the release branch
            safeDeleteBranch(releaseBranch);
            // TODO: make sure it is required and the right branch (master)
            // reset changes done on the original checkout branch (next dev version)
            safeRevertWorkingCopy();
        }
    }

    public void safeDeleteBranch(String branch) {
        try {
            scmManager.deleteBranch(branch);
        } catch (Exception e) {
            debuggingLogger.log(Level.FINE, "Failed to delete release branch: ", e);
            log("Failed to revert working copy: " + e.getLocalizedMessage());
        }
    }

    public void safeRevertWorkingCopy() {
        try {
            scmManager.revertWorkingCopy();
        } catch (Exception e) {
            debuggingLogger.log(Level.FINE, "Failed to revert working copy: ", e);
            log("Failed to revert working copy: " + e.getLocalizedMessage());
        }
    }

    public String getRemoteUrlForPom() {
        return null;
    }
}
