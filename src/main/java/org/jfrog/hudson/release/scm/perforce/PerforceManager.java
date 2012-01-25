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

import com.tek42.perforce.Depot;
import com.tek42.perforce.PerforceException;
import com.tek42.perforce.model.Label;
import hudson.model.AbstractBuild;
import hudson.model.TaskListener;
import hudson.plugins.perforce.PerforceSCM;
import hudson.plugins.perforce.PerforceTagAction;
import org.jfrog.hudson.action.ActionableHelper;
import org.jfrog.hudson.release.scm.AbstractScmManager;
import org.jfrog.hudson.release.scm.perforce.command.Edit;
import org.jfrog.hudson.release.scm.perforce.command.Revert;
import org.jfrog.hudson.release.scm.perforce.command.Submit;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Logger;

/**
 * Performs Perforce actions for the release management.
 *
 * @author Yossi Shaul
 */
public class PerforceManager extends AbstractScmManager<PerforceSCM> {
    private static Logger debuggingLogger = Logger.getLogger(PerforceManager.class.getName());

    public PerforceManager(AbstractBuild<?, ?> build, TaskListener buildListener) {
        super(build, buildListener);
    }

    public void commitWorkingCopy(String commitMessage) throws IOException, InterruptedException {
        submit(commitMessage);
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

    public void createTag(String tagUrl, String commitMessage) throws IOException, InterruptedException {
        PerforceTagAction perforceTagAction = ActionableHelper.getLatestAction(build, PerforceTagAction.class);
        //perforceTagAction.tagBuild(tagUrl, commitMessage);
        try {
            Depot depot = getDepot();
            Label label = new Label();
            label.setName(tagUrl);
            label.setDescription(commitMessage);
            label.setOwner(perforceTagAction.getOwner());
            label.setRevision(perforceTagAction.getChangeNumber() + "");
            List<String> viewPairs = parseViewPairs(perforceTagAction);
            //List<String> viewPairs = PerforceSCM.parseProjectPath(perforceTagAction.getView(), "workspace");
            for (int i = 0; i < viewPairs.size(); i += 2) {
                String depotPath = viewPairs.get(i);
                label.addView(depotPath);
            }
            depot.getLabels().saveLabel(label);
        } catch (PerforceException e) {
            throw new IOException("Failed to revert changelist: " + e.getMessage());
        }
    }

    private List<String> parseViewPairs(PerforceTagAction perforceTagAction) throws IOException {
        try {
            Method method = PerforceSCM.class.getDeclaredMethod("parseProjectPath", String.class, String.class);
            method.setAccessible(true);
            return (List<String>) method.invoke(null, perforceTagAction.getView(), "workspace");
        } catch (Exception e) {
            throw new IOException("Failed to parse view pair: " + e.getMessage());
        }
    }

    public String getRemoteUrl() {
        throw new UnsupportedOperationException("Remote URL not supported");
    }

    public void edit(File file) throws IOException {
        Depot depot = getDepot();
        try {
            new Edit(depot, file).editFile();
        } catch (PerforceException e) {
            throw new IOException("Failed to edit file: " + e.getMessage());
        }
    }

    private void submit(String message) throws IOException {
        Depot depot = getDepot();
        try {
            new Submit(depot).submit(message);
        } catch (PerforceException e) {
            throw new IOException("Failed to submit changelist: " + e.getMessage());
        }
    }

    public void revertWorkingCopy() throws IOException {
        try {
            Depot depot = getDepot();
            new Revert(depot).revert();
        } catch (PerforceException e) {
            throw new IOException("Failed to revert changelist: " + e.getMessage());
        }
    }
}
