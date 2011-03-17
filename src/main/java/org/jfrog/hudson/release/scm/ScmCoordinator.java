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

package org.jfrog.hudson.release.scm;

import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.scm.SCM;
import hudson.scm.SubversionSCM;
import org.jfrog.hudson.release.ReleaseAction;

import java.io.IOException;

/**
 * This class coordinates between the release steps and required scm actions based on the svm manager.
 *
 * @author Yossi Shaul
 */
public class ScmCoordinator {

    private final AbstractBuild build;
    private final BuildListener listener;
    private ScmManager scmManager;

    public ScmCoordinator(AbstractBuild build, BuildListener listener) {
        this.build = build;
        this.listener = listener;
    }

    public void prepare() throws IOException, InterruptedException {
        scmManager = createScmManager(build, listener);
        // TODO: remove prepare method from ScmManager
        scmManager.prepare();
    }

    public void afterSuccessfulReleaseVersionBuild() throws InterruptedException, IOException {
        if (isGit()) {
            // commit local changes
            scmManager.commitWorkingCopy("Committing release version");
        }

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
        if (isSvn()) {
            buildCompletedSvn();
        } else {
            buildCompletedGit();
        }
    }

    private void buildCompletedSvn() {
        if (build.getResult().isWorseThan(Result.SUCCESS)) {
            // build has failed, make sure to delete the tag and revert the working copy
            //run.getActions().remove(releaseBadge);
            ReleaseAction releaseAction = build.getAction(ReleaseAction.class);
            scmManager.safeRevertWorkingCopy();
            if (releaseAction.isTagCreated()) {
                scmManager.safeRevertTag(releaseAction.getTagUrl(), "Reverting vcs tag: " + releaseAction.getTagUrl());
            }
        }
    }

    private void buildCompletedGit() throws IOException, InterruptedException {
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

    private ScmManager createScmManager(AbstractBuild build, BuildListener listener) {
        SCM projectScm = build.getProject().getScm();
        if (projectScm instanceof SubversionSCM) {
            return new SubversionManager(build, listener);
        }
        // Git is optional SCM so we cannot use the class here
        if (projectScm.getClass().getName().equals("hudson.plugins.git.GitSCM")) {
            return new GitManager(build, listener);
        }
        throw new UnsupportedOperationException(
                "Scm of type: " + projectScm.getClass().getName() + " is not supported");
    }

    public String getRemoteUrlForPom() {
        // return remote url only for svn; git uses the same url
        return isGit() ? null : scmManager.getRemoteUrl();
    }

    boolean isSvn() {
        return scmManager instanceof SubversionManager;
    }

    boolean isGit() {
        return scmManager instanceof GitManager;
    }
}
