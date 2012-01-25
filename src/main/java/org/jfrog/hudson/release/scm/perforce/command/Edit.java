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

import java.io.File;
import java.io.Writer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Opens a file in the workspace for edit.
 *
 * @author Yossi Shaul
 * @see <a href="http://www.perforce.com/perforce/doc.current/manuals/cmdref/edit.html#1040665">http://www.perforce.com/perforce/doc.current/manuals/cmdref/edit.html#1040665</a>
 */
public class Edit extends AbstractPerforceTemplate {
    private static Logger debuggingLogger = Logger.getLogger(Edit.class.getName());

    /**
     * The file to open for edit
     */
    private final File file;

    public Edit(Depot depot, File file) {
        super(depot);
        this.file = file;
    }

    /**
     * Open file for edit
     */
    public void editFile() throws PerforceException {
        debuggingLogger.log(Level.FINE, "Opening file for edit: " + file.getAbsolutePath());
        EditBuilder builder = new EditBuilder();
        saveToPerforce(this, builder);
    }

    public File getFile() {
        return file;
    }

    public static class EditBuilder implements Builder<Edit> {

        public String[] getSaveCmd(String p4exe, Edit edit) {
            return new String[]{p4exe, "-s", "edit", edit.getFile().getAbsolutePath()};
        }

        public boolean requiresStandardInput() {
            return false;
        }

        public String[] getBuildCmd(String p4exe, String id) {
            throw new UnsupportedOperationException("Operation not supported in edit");
        }

        public Edit build(StringBuilder sb) throws PerforceException {
            throw new UnsupportedOperationException("Operation not supported in edit");
        }

        public void save(Edit obj, Writer writer) throws PerforceException {
            throw new UnsupportedOperationException("Operation not supported in edit");
        }
    }
}
