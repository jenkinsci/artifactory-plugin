/*
 * Copyright (C) 2011 JFrog Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jfrog.hudson.release.scm;

import hudson.FilePath;
import hudson.maven.agent.AbortException;
import hudson.model.AbstractBuild;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.scm.SubversionSCM;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider;
import org.tmatesoft.svn.core.wc.SVNCommitClient;
import org.tmatesoft.svn.core.wc.SVNCopyClient;
import org.tmatesoft.svn.core.wc.SVNCopySource;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Performs commit operations on Subversion repository configured for the project.
 *
 * @author Yossi Shaul
 */
public class SubversionManager extends AbstractScmManager<SubversionSCM> {
    private static Logger debuggingLogger = Logger.getLogger(SubversionManager.class.getName());

    public SubversionManager(AbstractBuild<?, ?> abstractBuild, TaskListener buildListener) {
        super(abstractBuild, buildListener);
    }

    public void prepare() {
        // nothing to do here
    }

    /**
     * Commits the working copy.
     *
     * @param commitMessageSuffix Suffix of the commit message (prefixed by '[artifactory-release]')
     * @return The commit info upon successful operation.
     * @throws IOException On IO of SVN failure
     */
    public SVNCommitInfo commitWorkingCopy(final String commitMessageSuffix) throws IOException, InterruptedException {
        return build.getWorkspace().act(new FilePath.FileCallable<SVNCommitInfo>() {
            public SVNCommitInfo invoke(File ws, VirtualChannel channel) throws IOException, InterruptedException {
                SubversionSCM.ModuleLocation location = getLocation();
                File workingCopy = new File(ws, location.getLocalDir()).getCanonicalFile();

                try {
                    SVNCommitClient commitClient = new SVNCommitClient(createAuthenticationManager(), null);
                    String commitMessage = COMMENT_PREFIX + commitMessageSuffix;
                    log(commitMessageSuffix);
                    SVNCommitInfo commitInfo = commitClient.doCommit(new File[]{workingCopy}, true,
                            commitMessage, null, null, true, true, SVNDepth.INFINITY);
                    SVNErrorMessage errorMessage = commitInfo.getErrorMessage();
                    if (errorMessage != null) {
                        throw new IOException("Failed to commit working copy: " + errorMessage.getFullMessage());
                    }
                    return commitInfo;
                } catch (SVNException e) {
                    throw new IOException(e);
                }
            }
        });
    }

    /**
     * Creates a tag directly from the working copy.
     *
     * @param tagUrl        The URL of the tag to create.
     * @param commitMessage Commit message
     * @return The commit info upon successful operation.
     * @throws IOException On IO of SVN failure
     */
    public SVNCommitInfo createTag(final String tagUrl, final String commitMessage)
            throws IOException, InterruptedException {
        return build.getWorkspace().act(new FilePath.FileCallable<SVNCommitInfo>() {
            public SVNCommitInfo invoke(File ws, VirtualChannel channel) throws IOException, InterruptedException {
                SubversionSCM.ModuleLocation moduleLocation = getLocation();
                File workingCopy = new File(ws, moduleLocation.getLocalDir()).getCanonicalFile();
                try {
                    SVNURL svnUrl = SVNURL.parseURIEncoded(tagUrl);
                    SVNCopyClient copyClient = new SVNCopyClient(createAuthenticationManager(), null);

                    log("Creating subversion tag: " + tagUrl);
                    SVNCopySource source = new SVNCopySource(SVNRevision.WORKING, SVNRevision.WORKING, workingCopy);
                    SVNCommitInfo commitInfo = copyClient.doCopy(new SVNCopySource[]{source},
                            svnUrl, false, true, true, commitMessage, new SVNProperties());

                    SVNErrorMessage errorMessage = commitInfo.getErrorMessage();
                    if (errorMessage != null) {
                        throw new IOException("Failed to create tag: " + errorMessage.getFullMessage());
                    }
                    return commitInfo;
                } catch (SVNException e) {
                    throw new IOException("Subversion tag creation failed: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Revert all the working copy changes.
     */
    public void revertWorkingCopy() throws IOException, InterruptedException {
        build.getWorkspace().act(new FilePath.FileCallable<Object>() {
            public Object invoke(File ws, VirtualChannel channel) throws IOException, InterruptedException {
                SubversionSCM.ModuleLocation location = getLocation();
                File workingCopy = new File(ws, location.getLocalDir()).getCanonicalFile();
                try {
                    log("Reverting working copy: " + workingCopy);
                    SVNWCClient wcClient = new SVNWCClient(createAuthenticationManager(), null);
                    wcClient.doRevert(new File[]{workingCopy}, SVNDepth.INFINITY, null);
                    return null;
                } catch (SVNException e) {
                    throw new IOException(e);
                }
            }
        });
    }

    /**
     * Attempts to revert the working copy. In case of failure it just logs the error.
     */
    public void safeRevertWorkingCopy() {
        try {
            revertWorkingCopy();
        } catch (Exception e) {
            debuggingLogger.log(Level.FINE, "Failed to revert working copy", e);
            log("Failed to revert working copy: " + e.getLocalizedMessage());
            Throwable cause = e.getCause();
            if (!(cause instanceof SVNException)) {
                return;
            }
            SVNException svnException = (SVNException) cause;
            if (svnException.getErrorMessage().getErrorCode() == SVNErrorCode.WC_LOCKED) {
                // work space locked attempt cleanup and try to revert again
                try {
                    cleanupWorkingCopy();
                } catch (Exception unlockException) {
                    debuggingLogger.log(Level.FINE, "Failed to cleanup working copy", e);
                    log("Failed to cleanup working copy: " + e.getLocalizedMessage());
                    return;
                }

                try {
                    revertWorkingCopy();
                } catch (Exception revertException) {
                    log("Failed to revert working copy on the 2nd attempt: " + e.getLocalizedMessage());
                }
            }
        }
    }

    private void cleanupWorkingCopy() throws IOException, InterruptedException {
        build.getWorkspace().act(new FilePath.FileCallable<Object>() {
            public Object invoke(File ws, VirtualChannel channel) throws IOException, InterruptedException {
                SubversionSCM.ModuleLocation location = getLocation();
                File workingCopy = new File(ws, location.getLocalDir()).getCanonicalFile();
                try {
                    log("Cleanup working copy: " + workingCopy);
                    SVNWCClient wcClient = new SVNWCClient(createAuthenticationManager(), null);
                    wcClient.doCleanup(workingCopy);
                    return null;
                } catch (SVNException e) {
                    throw new IOException(e);
                }
            }
        });
    }

    public void safeRevertTag(String tagUrl, String commitMessageSuffix) {
        try {
            log("Reverting subversion tag: " + tagUrl);
            SVNURL svnUrl = SVNURL.parseURIEncoded(tagUrl);
            SVNCommitClient commitClient = new SVNCommitClient(createAuthenticationManager(), null);
            SVNCommitInfo commitInfo = commitClient.doDelete(new SVNURL[]{svnUrl},
                    COMMENT_PREFIX + commitMessageSuffix);
            SVNErrorMessage errorMessage = commitInfo.getErrorMessage();
            if (errorMessage != null) {
                log("Failed to revert '" + tagUrl + "': " + errorMessage.getFullMessage());
            }
        } catch (SVNException e) {
            log("Failed to revert '" + tagUrl + "': " + e.getLocalizedMessage());
        }
    }

    public String getRemoteUrl() {
        return getLocation().remote;
    }

    public SubversionSCM.ModuleLocation getLocation() {
        return getHudsonScm().getLocations()[0];
    }

    private ISVNAuthenticationManager createAuthenticationManager() {
        ISVNAuthenticationProvider sap = getHudsonScm().getDescriptor().createAuthenticationProvider();
        if (sap == null) {
            throw new AbortException("Subversion authentication info is not set.");
        }

        ISVNAuthenticationManager sam = SVNWCUtil.createDefaultAuthenticationManager();
        sam.setAuthenticationProvider(sap);
        return sam;
    }
}
