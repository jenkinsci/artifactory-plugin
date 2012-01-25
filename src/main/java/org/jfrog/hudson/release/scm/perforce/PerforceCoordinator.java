/*
 * Copyright (C) 2012 JFrog Ltd.
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

package org.jfrog.hudson.release.scm.perforce;

import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.remoting.VirtualChannel;
import org.jfrog.hudson.release.ReleaseAction;
import org.jfrog.hudson.release.scm.AbstractScmCoordinator;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Perforce scm coordinator. Interacts with the {@link PerforceManager} to fulfill the release process.
 *
 * @author Yossi Shaul
 */
public class PerforceCoordinator extends AbstractScmCoordinator {
    private static Logger debuggingLogger = Logger.getLogger(PerforceManager.class.getName());

    private PerforceManager perforceManager;
    private final ReleaseAction releaseAction;
    private boolean tagCreated;

    public PerforceCoordinator(AbstractBuild build, BuildListener listener, ReleaseAction releaseAction) {
        super(build, listener);
        this.releaseAction = releaseAction;
    }

    public void prepare() throws IOException, InterruptedException {
        perforceManager = new PerforceManager(build, listener);
    }

    @Override
    public void edit(FilePath filePath) throws IOException, InterruptedException {
        filePath.act(new FilePath.FileCallable<Object>() {
            public Object invoke(File file, VirtualChannel channel) throws IOException, InterruptedException {
                perforceManager.edit(file);
                return null;
            }
        });
    }

    public void afterSuccessfulReleaseVersionBuild() throws InterruptedException, IOException {
        if (modifiedFilesForReleaseVersion) {
            log("Submitting release version changes");
            perforceManager.commitWorkingCopy(releaseAction.getDefaultReleaseComment());
        }

        if (releaseAction.isCreateVcsTag()) {
            log("Creating label: '" + releaseAction.getTagUrl() + "'");
            perforceManager.createTag(releaseAction.getTagUrl(), releaseAction.getTagComment());
            tagCreated = true;
        }
    }

    public void beforeDevelopmentVersionChange() throws IOException, InterruptedException {

    }

    public void buildCompleted() throws IOException, InterruptedException {
        if (build.getResult().isBetterOrEqualTo(Result.SUCCESS)) {
            // submit the next development version
            log("Submitting next development version changes");
            perforceManager.commitWorkingCopy(releaseAction.getNextDevelCommitComment());
        } else {
            safeRevertWorkingCopy();
            if (tagCreated) {
                safeDeleteLabel();
            }
        }
    }

    private void safeRevertWorkingCopy() {
        log("Reverting local changes");
        try {
            perforceManager.revertWorkingCopy();
        } catch (Exception e) {
            debuggingLogger.log(Level.FINE, "Failed to revert: ", e);
            log("Failed to revert: " + e.getLocalizedMessage());
        }
    }

    private void safeDeleteLabel() throws IOException {
        log("Deleting label '" + releaseAction.getTagUrl() + "'");
        try {
            perforceManager.deleteLabel(releaseAction.getTagUrl());
        } catch (Exception e) {
            debuggingLogger.log(Level.FINE, "Failed to delete label: ", e);
            log("Failed to delete label: " + e.getLocalizedMessage());
        }
    }

    public String getRemoteUrlForPom() {
        return null;
    }
}
