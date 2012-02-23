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

import com.perforce.p4java.core.IChangelist;
import com.tek42.perforce.Depot;
import hudson.model.AbstractBuild;
import hudson.model.TaskListener;
import hudson.plugins.perforce.PerforceSCM;
import hudson.plugins.perforce.PerforceTagAction;
import org.jfrog.build.vcs.perforce.PerforceClient;
import org.jfrog.hudson.action.ActionableHelper;
import org.jfrog.hudson.release.scm.AbstractScmManager;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;

/**
 * Performs Perforce actions for the release management.
 *
 * @author Yossi Shaul
 */
public class PerforceManager extends AbstractScmManager<PerforceSCM> {

    private PerforceClient perforce;

    public PerforceManager(AbstractBuild<?, ?> build, TaskListener buildListener) {
        super(build, buildListener);
    }

    public void prepare() throws IOException {
        PerforceClient.Builder builder = new PerforceClient.Builder();
        Depot perforceDepot = getDepot();
        String hostAddress = perforceDepot.getPort();
        if (!hostAddress.contains(":")) {
            hostAddress = "localhost:" + hostAddress;
        }
        builder.hostAddress(hostAddress).client(perforceDepot.getClient());
        builder.username(perforceDepot.getUser()).password(perforceDepot.getPassword());
        perforce = builder.build();
    }

    public void commitWorkingCopy(int changeListId, String commitMessage) throws IOException {
        perforce.commitWorkingCopy(changeListId, commitMessage);
    }

    public void createTag(String label, String commitMessage, String changeListId) throws IOException {
        perforce.createLabel(label, commitMessage, changeListId);
    }

    public void revertWorkingCopy(int changeListId) throws IOException {
        perforce.revertWorkingCopy(changeListId);
    }

    public void deleteLabel(String tagUrl) throws IOException {
        perforce.deleteLabel(tagUrl);
    }

    public void edit(int changeListId, File releaseVersion) throws IOException {
        perforce.editFile(changeListId, releaseVersion);
    }

    /**
     * Creates a new changelist and returns its id number
     * @return The id of the newly created changelist
     * @throws IOException In case of errors communicating with perforce server
     */
    public int createNewChangeList() throws IOException {
        return perforce.createNewChangeList();
    }

    public void deleteChangeList(int changeListId) throws IOException {
        perforce.deleteChangeList(changeListId);
    }

    public int getDefaultChangeListId() throws IOException {
        return perforce.getDefaultChangeListId();
    }

    private Depot getDepot() {
        try {
            PerforceTagAction perforceTagAction = ActionableHelper.getLatestAction(build, PerforceTagAction.class);
            Field depotField = perforceTagAction.getClass().getDeclaredField("depot");
            depotField.setAccessible(true);
            return (Depot) depotField.get(perforceTagAction);
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve depot: " + e.getMessage(), e);
        }
    }

    public void commitWorkingCopy(String commitMessage) throws IOException, InterruptedException {
        throw new UnsupportedOperationException("Use the overloaded method");
    }

    public void createTag(String tagUrl, String commitMessage) throws IOException, InterruptedException {
        throw new UnsupportedOperationException("Use the overloaded method");
    }

    public String getRemoteUrl() {
        throw new UnsupportedOperationException("Remote URL not supported");
    }
}
