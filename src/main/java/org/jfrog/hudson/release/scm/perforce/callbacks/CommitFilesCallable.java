package org.jfrog.hudson.release.scm.perforce.callbacks;

import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import org.jfrog.build.vcs.perforce.PerforceClient;

import java.io.File;
import java.io.IOException;

/**
 * @author Shay Yaakov
 */
public class CommitFilesCallable implements FilePath.FileCallable<String>{

    private PerforceClient perforceClient;
    private int changeListId;
    private String commitMessage;

    public CommitFilesCallable(PerforceClient perforceClient, int changeListId, String commitMessage) {
        this.perforceClient = perforceClient;
        this.changeListId = changeListId;
        this.commitMessage = commitMessage;
    }

    public String invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
        perforceClient.commitWorkingCopy(changeListId, commitMessage);
        return null;
    }
}
