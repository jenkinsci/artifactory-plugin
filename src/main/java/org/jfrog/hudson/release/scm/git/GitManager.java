/*
 * Copyright (C) 2011 JFrog Ltd.
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

package org.jfrog.hudson.release.scm.git;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.TaskListener;
import hudson.plugins.git.GitAPI;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.remoting.VirtualChannel;
import org.apache.commons.lang.StringUtils;
import org.jfrog.hudson.release.scm.AbstractScmManager;
import org.spearce.jgit.transport.RemoteConfig;
import org.spearce.jgit.transport.URIish;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Interacts with Git repository for the various release operations.
 *
 * @author Yossi Shaul
 */
public class GitManager extends AbstractScmManager<GitSCM> {
    private static Logger debuggingLogger = Logger.getLogger(GitManager.class.getName());
    private String baseCommit;

    public GitManager(AbstractBuild<?, ?> build, TaskListener buildListener) {
        super(build, buildListener);
    }

    public void prepare() throws IOException, InterruptedException {
        build.getWorkspace().act(new FilePath.FileCallable<String>() {
            public String invoke(File ws, VirtualChannel channel) throws IOException, InterruptedException {
                try {
                    GitAPI git = createGitAPI(ws);
                    // commit all the modified files
                    baseCommit = git.launchCommand("rev-parse", "--verify", "HEAD");
                    debuggingLogger.fine(String.format("Base commit hash%s", baseCommit));
                    return baseCommit;
                } catch (GitException e) {
                    throw new IOException("Failed retrieving current commit hash: " + e.getMessage());
                }
            }
        });
    }

    public Object commitWorkingCopy(final String commitMessageSuffix) throws IOException, InterruptedException {
        return build.getWorkspace().act(new FilePath.FileCallable<String>() {
            public String invoke(File ws, VirtualChannel channel) throws IOException, InterruptedException {
                try {
                    GitAPI git = createGitAPI(ws);
                    // commit all the modified files
                    String commitMessage = COMMENT_PREFIX + commitMessageSuffix;
                    log(commitMessageSuffix);
                    String commitOutput = git.launchCommand("commit", "--all", "-m", commitMessage);
                    debuggingLogger.fine(String.format("Reset command output:%n%s", commitOutput));
                    return commitOutput;
                } catch (GitException e) {
                    throw new IOException("Git working copy commit failed: " + e.getMessage());
                }
            }
        });
    }

    public Object createTag(final String tagName, final String commitMessage)
            throws IOException, InterruptedException {
        return build.getWorkspace().act(new FilePath.FileCallable<String>() {
            public String invoke(File ws, VirtualChannel channel) throws IOException, InterruptedException {
                try {
                    String escapedTagName = tagName.replace(' ', '_');
                    GitAPI git = createGitAPI(ws);
                    if (git.tagExists(escapedTagName)) {
                        throw new IOException("Git tag '" + escapedTagName + "' already exists");
                    }
                    log("Creating git tag: " + escapedTagName);
                    String tagOutput = git.launchCommand("tag", "-a", escapedTagName, "-m", commitMessage);
                    debuggingLogger.fine(String.format("Tag command output:%n%s", tagOutput));
                    return tagOutput;
                } catch (GitException e) {
                    throw new IOException("Git tag creation failed: " + e.getMessage());
                }
            }
        });
    }

    public void push() throws IOException, InterruptedException {
        build.getWorkspace().act(new FilePath.FileCallable<String>() {
            public String invoke(File workspace, VirtualChannel channel) throws IOException, InterruptedException {
                try {
                    GitAPI git = createGitAPI(workspace);
                    log("Pushing git changes");
                    String pushOutput = git.launchCommand("push", getRemoteUrl());
                    debuggingLogger.fine(String.format("Push command output:%n%s", pushOutput));
                    return pushOutput;
                } catch (GitException e) {
                    throw new IOException("Failed to reset working copy: " + e.getMessage());
                }
            }
        });
    }

    public void pushTag(final String tagName) throws IOException, InterruptedException {
        build.getWorkspace().act(new FilePath.FileCallable<String>() {
            public String invoke(File workspace, VirtualChannel channel) throws IOException, InterruptedException {
                try {
                    String escapedTagName = tagName.replace(' ', '_');
                    GitAPI git = createGitAPI(workspace);
                    log("Pushing git tag: " + escapedTagName);
                    String remoteUri = getRemoteUrl();
                    String pushOutput = git.launchCommand("push", remoteUri, escapedTagName);
                    debuggingLogger.fine(String.format("Push tag command output:%n%s", pushOutput));
                    return pushOutput;
                } catch (GitException e) {
                    throw new IOException("Failed to reset working copy: " + e.getMessage());
                }
            }
        });
    }

    public void revertWorkingCopy() throws IOException, InterruptedException {
        build.getWorkspace().act(new FilePath.FileCallable<String>() {
            public String invoke(File workspace, VirtualChannel channel) throws IOException, InterruptedException {
                try {
                    GitAPI git = createGitAPI(workspace);
                    log("Reverting git working copy back to initial commit: " + getWorkingDirectory(workspace));
                    String resetOutput = git.launchCommand("reset", "--hard", baseCommit);
                    debuggingLogger.fine(String.format("Reset command output:%n%s", resetOutput));
                    return resetOutput;
                } catch (GitException e) {
                    throw new IOException("Failed to reset working copy: " + e.getMessage());
                }
            }
        });
    }

    public void safeRevertWorkingCopy() {
        try {
            revertWorkingCopy();
        } catch (Exception e) {
            debuggingLogger.log(Level.FINE, "Failed to revert working copy: ", e);
            log("Failed to revert working copy: " + e.getLocalizedMessage());
        }
    }

    public void safeRevertTag(String tagUrl, String commitMessageSuffix) {
        throw new UnsupportedOperationException("Tag revert not implemented for git. Should revert all local commits.");
    }

    public String getRemoteUrl() {
        RemoteConfig remoteConfig = getHudsonScm().getRepositories().get(0);
        URIish uri = remoteConfig.getURIs().get(0);
        return uri.toPrivateString();
    }

    private GitAPI createGitAPI(File workspace) throws IOException, InterruptedException {
        GitSCM gitSCM = getHudsonScm();
        File workingCopy = getWorkingDirectory(workspace);
        String gitExe = gitSCM.getGitExe(build.getBuiltOn(), buildListener);

        EnvVars gitEnvironment = build.getEnvironment(buildListener);
        String confName = gitSCM.getGitConfigNameToUse();
        if (StringUtils.isNotBlank(confName)) {
            gitEnvironment.put("GIT_COMMITTER_NAME", confName);
            gitEnvironment.put("GIT_AUTHOR_NAME", confName);
        }
        String confEmail = gitSCM.getGitConfigEmailToUse();
        if (StringUtils.isNotBlank(confEmail)) {
            gitEnvironment.put("GIT_COMMITTER_EMAIL", confEmail);
            gitEnvironment.put("GIT_AUTHOR_EMAIL", confEmail);
        }

        GitAPI git = new GitAPI(gitExe, new FilePath(workingCopy), buildListener, new EnvVars());
        return git;
    }

    private File getWorkingDirectory(File ws) throws IOException {
        // working directory might be relative to the workspace
        GitSCM gitSCM = getHudsonScm();
        String relativeTargetDir = gitSCM.getRelativeTargetDir() == null ? "" : gitSCM.getRelativeTargetDir();
        return new File(ws, relativeTargetDir).getCanonicalFile();
    }
}
