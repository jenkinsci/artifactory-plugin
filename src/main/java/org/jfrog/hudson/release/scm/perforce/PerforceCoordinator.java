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
import org.jfrog.hudson.release.ReleaseAction;
import org.jfrog.hudson.release.scm.AbstractScmCoordinator;
import org.jfrog.hudson.util.ExtractorUtils;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Perforce scm coordinator. Interacts with the {@link AbstractPerforceManager} to fulfill the release process.
 *
 * @author Yossi Shaul
 */
public class PerforceCoordinator extends AbstractScmCoordinator {
    private static Logger debuggingLogger = Logger.getLogger(AbstractPerforceManager.class.getName());

    private AbstractPerforceManager perforce;
    private final ReleaseAction releaseAction;
    private boolean tagCreated;
    private int currentChangeListId;

    public PerforceCoordinator(AbstractBuild build, BuildListener listener, ReleaseAction releaseAction,
                               AbstractPerforceManager perforce) {
        super(build, listener);
        this.perforce = perforce;
        this.releaseAction = releaseAction;
    }

    public void prepare() throws IOException, InterruptedException {
        perforce.prepare();
    }

    @Override
    public void beforeReleaseVersionChange() throws IOException {
        currentChangeListId = perforce.createNewChangeList();
    }

    public void afterSuccessfulReleaseVersionBuild() throws Exception {
        String labelChangeListId = ExtractorUtils.getVcsRevision(build.getEnvironment(listener));
        if (modifiedFilesForReleaseVersion) {
            log("Submitt  ing release version changes");
            labelChangeListId = currentChangeListId + "";
            perforce.commitWorkingCopy(currentChangeListId, releaseAction.getDefaultGlobalReleaseVersion());
        } else {
            safeRevertWorkingCopy();
            currentChangeListId = perforce.getDefaultChangeListId();
        }

        if (releaseAction.isCreateVcsTag()) {
            log("Creating label: '" + releaseAction.getTagUrl() + "' with change list id: " + labelChangeListId);
            perforce.createTag(releaseAction.getTagUrl(), releaseAction.getTagComment(), labelChangeListId);
            tagCreated = true;
        }
    }

    public void beforeDevelopmentVersionChange() throws IOException, InterruptedException {
        currentChangeListId = perforce.getDefaultChangeListId();
    }

    @Override
    public void afterDevelopmentVersionChange(boolean modified) throws IOException, InterruptedException {
        super.afterDevelopmentVersionChange(modified);
        if (modified) {
            log("Submitting next development version changes");
            try {
                perforce.commitWorkingCopy(currentChangeListId, releaseAction.getNextDevelCommitComment());
            } catch (Exception e) {
                log("Error: " + e.getMessage());
            }
        } else {
            safeRevertWorkingCopy();
            currentChangeListId = perforce.getDefaultChangeListId();
        }
    }

    @Override
    public void edit(FilePath filePath) throws IOException, InterruptedException {
        try {
            perforce.edit(currentChangeListId, filePath);
        } catch (Exception e) {
            log("Error: " + e.getMessage());
        }
    }

    public void buildCompleted() throws IOException, InterruptedException {
        if (!build.getResult().isBetterOrEqualTo(Result.SUCCESS)) {
            safeRevertWorkingCopy();
            if (tagCreated) {
                safeDeleteLabel();
            }
        } else {
            log("Closing connection to perforce server");
            perforce.closeConnection();
        }
    }

    private void safeRevertWorkingCopy() {
        log("Reverting local changes");
        try {
            perforce.revertWorkingCopy(currentChangeListId);
        } catch (Exception e) {
            debuggingLogger.log(Level.FINE, "Failed to revert: ", e);
            log("Failed to revert: " + e.getLocalizedMessage());
        }
    }

    private void safeDeleteLabel() throws IOException {
        log("Deleting label '" + releaseAction.getTagUrl() + "'");
        try {
            perforce.deleteLabel(releaseAction.getTagUrl());
        } catch (Exception e) {
            debuggingLogger.log(Level.FINE, "Failed to delete label: ", e);
            log("Failed to delete label: " + e.getLocalizedMessage());
        }
    }

    public String getRemoteUrlForPom() {
        return null;
    }
}