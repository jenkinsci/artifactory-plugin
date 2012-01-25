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

package org.jfrog.hudson.release.scm.perforce.command;

import com.tek42.perforce.Depot;
import com.tek42.perforce.PerforceException;
import com.tek42.perforce.parse.AbstractPerforceTemplate;
import com.tek42.perforce.parse.Builder;

import java.io.Writer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Deletes (unlocked) perforce label.
 *
 * @author Yossi Shaul
 * @see <a href="http://www.perforce.com/perforce/doc.current/manuals/cmdref/label.html">http://www.perforce.com/perforce/doc.current/manuals/cmdref/label.html</a>
 */
public class DeleteLabel extends AbstractPerforceTemplate {
    private static Logger debuggingLogger = Logger.getLogger(DeleteLabel.class.getName());
    private final String labelName;

    public DeleteLabel(Depot depot, String labelName) {
        super(depot);
        this.labelName = labelName;
    }

    /**
     * Delete the remote label.
     */
    public void deleteLabel() throws PerforceException {
        debuggingLogger.log(Level.FINE, "Deleting label: " + labelName);
        DeleteLabelBuilder builder = new DeleteLabelBuilder();
        saveToPerforce(this, builder);
    }

    public String getLabelName() {
        return labelName;
    }

    public static class DeleteLabelBuilder implements Builder<DeleteLabel> {

        public String[] getSaveCmd(String p4exe, DeleteLabel edit) {
            return new String[]{p4exe, "-s", "label", "-d", edit.getLabelName()};
        }

        public boolean requiresStandardInput() {
            return false;
        }

        public String[] getBuildCmd(String p4exe, String id) {
            throw new UnsupportedOperationException("Operation not supported");
        }

        public DeleteLabel build(StringBuilder sb) throws PerforceException {
            throw new UnsupportedOperationException("Operation not supported");
        }

        public void save(DeleteLabel obj, Writer writer) throws PerforceException {
            throw new UnsupportedOperationException("Operation not supported");
        }
    }
}
