package org.jfrog.hudson.release.scm.perforce.callbacks;

import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import org.jfrog.build.vcs.perforce.PerforceClient;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;

/**
 * @author Shay Yaakov
 */
public class EditFilesCallable implements FilePath.FileCallable<String>, Serializable {

    private PerforceClient perforce;
    private int currentChangeListId;

    public EditFilesCallable(PerforceClient perforce, int currentChangeListId) {
        this.perforce = perforce;
        this.currentChangeListId = currentChangeListId;
    }

    public String invoke(File file, VirtualChannel channel) throws IOException, InterruptedException {
        perforce.editFile(currentChangeListId, file);
        return null;
    }

}

