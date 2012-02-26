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
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.TaskListener;
import hudson.plugins.perforce.PerforceSCM;
import hudson.plugins.perforce.PerforceTagAction;
import hudson.remoting.VirtualChannel;
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

    /**
     * This builder is passed to {@link EditFilesCallable} for creating a new perforce
     * connection since the operation may occur on remote agents.
     */
    private PerforceClient.Builder builder;
    private PerforceClient perforce;

    public PerforceManager(AbstractBuild<?, ?> build, TaskListener buildListener) {
        super(build, buildListener);
    }

    public void prepare() throws IOException, InterruptedException {
        builder = new PerforceClient.Builder();
        PerforceSCM jenkinsScm = getJenkinsScm();
        String hostAddress = jenkinsScm.getP4Port();
        if (!hostAddress.contains(":")) {
            hostAddress = "localhost:" + hostAddress;
        }
        builder.hostAddress(hostAddress).client(build.getEnvironment(buildListener).get("P4CLIENT"));
        builder.username(jenkinsScm.getP4User()).password(jenkinsScm.getDecryptedP4Passwd());
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

    public void commitWorkingCopy(String commitMessage) throws IOException, InterruptedException {
        throw new UnsupportedOperationException("Use the overloaded method");
    }

    public void createTag(String tagUrl, String commitMessage) throws IOException, InterruptedException {
        throw new UnsupportedOperationException("Use the overloaded method");
    }

    public String getRemoteUrl() {
        throw new UnsupportedOperationException("Remote URL not supported");
    }

    /**
     * Opens file for editing, this method uses {@link EditFilesCallable} which opens
     * new connection to perforce server since it may invoke on remote agents.
     * @param currentChangeListId The current change list id to open the file for editing at
     * @param filePath The filePath which contains the file we need to edit
     * @throws IOException Thrown in case of perforce communication errors
     * @throws InterruptedException
     */
    public void edit(int currentChangeListId, FilePath filePath) throws IOException, InterruptedException {
        filePath.act(new EditFilesCallable(builder, buildListener, currentChangeListId));
    }

    public void closeConnection() throws IOException {
        perforce.closeConnection();   
    }

    private static class EditFilesCallable implements FilePath.FileCallable<String> {
        private PerforceClient.Builder builder;
        private int currentChangeListId;
        private TaskListener listener;

        public EditFilesCallable(PerforceClient.Builder builder, TaskListener buildListener, int currentChangeListId) {
            this.builder = builder;
            this.listener = buildListener;
            this.currentChangeListId = currentChangeListId;
        }

        public String invoke(File file, VirtualChannel channel) throws IOException, InterruptedException {
            log(listener, "Opening file: '" + file.getAbsolutePath() + "' for editing");
            PerforceClient perforce = builder.build();
            String statusMessage = perforce.editFile(currentChangeListId, file);
            log(listener, "Got status message: '" + statusMessage + "'");
            perforce.closeConnection();
            return null;
        }
    }
}
