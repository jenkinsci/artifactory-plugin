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
import hudson.util.ArgumentListBuilder;
import org.apache.commons.lang.StringUtils;
import org.jfrog.hudson.release.scm.AbstractScmManager;
import org.spearce.jgit.transport.RemoteConfig;
import org.spearce.jgit.transport.URIish;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Interacts with Git repository for the various release operations.
 *
 * @author Yossi Shaul
 */
public class GitManager extends AbstractScmManager<GitSCM> {
    private static Logger debuggingLogger = Logger.getLogger(GitManager.class.getName());

    public GitManager(AbstractBuild<?, ?> build, TaskListener buildListener) {
        super(build, buildListener);
    }

    public String getCurrentCommitHash() throws IOException, InterruptedException {
        FilePath workspace = build.getWorkspace();
        return workspace.act(new CurrentCommitCallable(workspace, getHudsonScm(), build, buildListener));
    }

    public String checkoutBranch(final String branch, final boolean create) throws IOException, InterruptedException {
        FilePath workspace = build.getWorkspace();
        return workspace
                .act(new CheckoutBranchCallable(create, branch, getHudsonScm(), buildListener, workspace, build));
    }

    public void commitWorkingCopy(final String commitMessage) throws IOException, InterruptedException {
        FilePath workspace = build.getWorkspace();
        workspace.act(new CommitWorkingCopyCallable(commitMessage, getHudsonScm(), buildListener, workspace, build));
    }

    public void createTag(final String tagName, final String commitMessage)
            throws IOException, InterruptedException {
        FilePath workspace = build.getWorkspace();
        workspace.act(new CreateTagCallable(tagName, commitMessage, getHudsonScm(), buildListener, workspace, build));
    }

    public String push(final String remoteRepository, final String branch) throws IOException, InterruptedException {
        FilePath workspace = build.getWorkspace();
        return workspace
                .act(new PushCallable(branch, remoteRepository, getHudsonScm(), buildListener, workspace, build));
    }

    public String pushTag(final String remoteRepository, final String tagName)
            throws IOException, InterruptedException {
        FilePath workspace = build.getWorkspace();
        return workspace
                .act(new PushTagCallable(tagName, remoteRepository, getHudsonScm(), buildListener, workspace, build));
    }

    public String pull(final String remoteRepository, final String branch) throws IOException, InterruptedException {
        FilePath workspace = build.getWorkspace();
        return workspace
                .act(new PullCallable(remoteRepository, branch, getHudsonScm(), buildListener, workspace, build));
    }

    public void revertWorkingCopy(final String commitIsh) throws IOException, InterruptedException {
        FilePath workspace = build.getWorkspace();
        workspace.act(new RevertCallable(commitIsh, getHudsonScm(), buildListener, workspace, build));
    }

    public String deleteLocalBranch(final String branch) throws IOException, InterruptedException {
        FilePath workspace = build.getWorkspace();
        return workspace.act(new DeleteLocalBranchCallable(branch, getHudsonScm(), buildListener, workspace, build));
    }

    public String deleteRemoteBranch(final String remoteRepository, final String branch)
            throws IOException, InterruptedException {
        FilePath workspace = build.getWorkspace();
        return workspace
                .act(new DeleteRemoteBranchCallable(branch, remoteRepository, getHudsonScm(), buildListener, workspace,
                        build));
    }

    public String deleteLocalTag(final String tag) throws IOException, InterruptedException {
        FilePath workspace = build.getWorkspace();
        return workspace.act(new DeleteLocalTagCallable(tag, getHudsonScm(), buildListener, workspace, build));
    }

    public String deleteRemoteTag(final String remoteRepository, final String tag)
            throws IOException, InterruptedException {
        FilePath workspace = build.getWorkspace();
        return workspace.act(
                new DeleteRemoteTagCallable(tag, remoteRepository, getHudsonScm(), buildListener, workspace, build));
    }

    public String getRemoteUrl() {
        RemoteConfig remoteConfig = getHudsonScm().getRepositories().get(0);
        URIish uri = remoteConfig.getURIs().get(0);
        return uri.toPrivateString();
    }

    private static GitAPI createGitAPI(FilePath workspace, GitSCM gitSCM, EnvVars gitEnvironment,
            TaskListener buildListener, String gitExe) throws IOException {
        File workingCopy = getWorkingDirectory(gitSCM, new File(workspace.getRemote()));
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

    private static File getWorkingDirectory(GitSCM gitSCM, File ws) throws IOException {
        // working directory might be relative to the workspace
        String relativeTargetDir = gitSCM.getRelativeTargetDir() == null ? "" : gitSCM.getRelativeTargetDir();
        return new File(ws, relativeTargetDir).getCanonicalFile();
    }

    private static class CurrentCommitCallable implements FilePath.FileCallable<String> {
        private final GitSCM gitSCM;
        private final TaskListener listener;
        private final FilePath workspace;
        private final EnvVars envVars;
        private final String gitExe;

        private CurrentCommitCallable(FilePath workspace, GitSCM gitSCM, AbstractBuild build, TaskListener listener)
                throws IOException, InterruptedException {
            this.workspace = workspace;
            this.gitSCM = gitSCM;
            this.listener = listener;
            this.envVars = build.getEnvironment(listener);
            this.gitExe = gitSCM.getGitExe(build.getBuiltOn(), listener);
        }

        public String invoke(File ws, VirtualChannel channel) throws IOException, InterruptedException {
            try {
                GitAPI git = createGitAPI(workspace, gitSCM, envVars, listener, gitExe);
                // commit all the modified files
                String baseCommit = git.launchCommand("rev-parse", "--verify", "HEAD").trim();
                debuggingLogger.fine(String.format("Base commit hash%s", baseCommit));
                return baseCommit;
            } catch (GitException e) {
                throw new IOException("Failed retrieving current commit hash: " + e.getMessage());
            }
        }
    }

    private static class CheckoutBranchCallable implements FilePath.FileCallable<String> {
        private final boolean create;
        private final String branch;
        private final GitSCM gitSCM;
        private final TaskListener listener;
        private final FilePath workspace;
        private final EnvVars envVars;
        private final String gitExe;

        public CheckoutBranchCallable(boolean create, String branch, GitSCM gitSCM, TaskListener listener,
                FilePath workspace, AbstractBuild build) throws IOException, InterruptedException {
            this.create = create;
            this.branch = branch;
            this.gitSCM = gitSCM;
            this.listener = listener;
            this.workspace = workspace;
            this.envVars = build.getEnvironment(listener);
            this.gitExe = gitSCM.getGitExe(build.getBuiltOn(), listener);
        }

        public String invoke(File ws, VirtualChannel channel) throws IOException, InterruptedException {
            try {
                // commit all the modified files
                ArgumentListBuilder args = new ArgumentListBuilder("checkout");
                if (create) {
                    args.add("-b"); // force create new branch
                }
                args.add(branch);
                GitAPI git = createGitAPI(workspace, gitSCM, envVars, listener, gitExe);
                String checkoutResult = git.launchCommand(args);
                debuggingLogger.fine(String.format("Checkout result: %s", checkoutResult));
                return checkoutResult;
            } catch (GitException e) {
                throw new IOException("Failed checkout branch: " + e.getMessage());
            }
        }
    }

    private static class CommitWorkingCopyCallable implements FilePath.FileCallable<String> {
        private final GitSCM gitSCM;
        private final TaskListener listener;
        private final FilePath workspace;
        private final EnvVars envVars;
        private final String gitExe;
        private final String commitMessage;

        public CommitWorkingCopyCallable(String commitMessage, GitSCM gitSCM, TaskListener listener,
                FilePath workspace, AbstractBuild build) throws IOException, InterruptedException {
            this.commitMessage = commitMessage;
            this.envVars = build.getEnvironment(listener);
            this.gitExe = gitSCM.getGitExe(build.getBuiltOn(), listener);
            this.gitSCM = gitSCM;
            this.listener = listener;
            this.workspace = workspace;
        }

        public String invoke(File ws, VirtualChannel channel) throws IOException, InterruptedException {
            try {
                // commit all the modified files
                GitAPI git = createGitAPI(workspace, gitSCM, envVars, listener, gitExe);
                String commitOutput = git.launchCommand("commit", "--all", "-m", commitMessage);
                debuggingLogger.fine(String.format("Reset command output:%n%s", commitOutput));
                return commitOutput;
            } catch (GitException e) {
                throw new IOException("Git working copy commit failed: " + e.getMessage());
            }
        }
    }

    private static class CreateTagCallable implements FilePath.FileCallable<String> {
        private final String tagName;
        private final String commitMessage;
        private final GitSCM gitSCM;
        private final TaskListener listener;
        private final FilePath workspace;
        private final EnvVars envVars;
        private final String gitExe;

        public CreateTagCallable(String tagName, String commitMessage, GitSCM gitSCM, TaskListener listener,
                FilePath workspace, AbstractBuild build) throws IOException, InterruptedException {
            this.tagName = tagName;
            this.commitMessage = commitMessage;
            this.envVars = build.getEnvironment(listener);
            this.gitExe = gitSCM.getGitExe(build.getBuiltOn(), listener);
            this.listener = listener;
            this.gitSCM = gitSCM;
            this.workspace = workspace;
        }

        public String invoke(File ws, VirtualChannel channel) throws IOException, InterruptedException {
            try {
                String escapedTagName = tagName.replace(' ', '_');
                log(listener, String.format("Creating tag '%s'", escapedTagName));
                GitAPI git = createGitAPI(workspace, gitSCM, envVars, listener, gitExe);
                if (git.tagExists(escapedTagName)) {
                    throw new IOException("Git tag '" + escapedTagName + "' already exists");
                }
                String tagOutput = git.launchCommand("tag", "-a", escapedTagName, "-m", commitMessage);
                debuggingLogger.fine(String.format("Tag command output:%n%s", tagOutput));
                return tagOutput;
            } catch (GitException e) {
                throw new IOException("Git tag creation failed: " + e.getMessage());
            }
        }
    }

    private static class PushCallable implements FilePath.FileCallable<String> {
        private final String branch;
        private final String remoteRepository;
        private final GitSCM gitSCM;
        private final TaskListener listener;
        private final FilePath workspace;
        private final EnvVars envVars;
        private final String gitExe;


        public PushCallable(String branch, String remoteRepository, GitSCM gitSCM, TaskListener listener,
                FilePath workspace, AbstractBuild build) throws IOException, InterruptedException {
            this.branch = branch;
            this.remoteRepository = remoteRepository;
            this.envVars = build.getEnvironment(listener);
            this.gitExe = gitSCM.getGitExe(build.getBuiltOn(), listener);
            this.listener = listener;
            this.gitSCM = gitSCM;
            this.workspace = workspace;
        }

        public String invoke(File ws, VirtualChannel channel) throws IOException, InterruptedException {
            try {
                log(listener, String.format("Pushing branch '%s' to '%s'", branch, remoteRepository));
                GitAPI git = createGitAPI(workspace, gitSCM, envVars, listener, gitExe);
                String pushOutput = git.launchCommand("push", remoteRepository, "refs/heads/" + branch);
                debuggingLogger.fine(String.format("Push command output:%n%s", pushOutput));
                return pushOutput;
            } catch (GitException e) {
                throw new IOException("Failed to push: " + e.getMessage());
            }
        }
    }

    private static class PushTagCallable implements FilePath.FileCallable<String> {
        private final GitSCM gitSCM;
        private final TaskListener listener;
        private final FilePath workspace;
        private final EnvVars envVars;
        private final String gitExe;
        private final String tagName;
        private final String remoteRepository;

        public PushTagCallable(String tagName, String remoteRepository, GitSCM gitSCM, TaskListener listener,
                FilePath workspace, AbstractBuild build) throws IOException, InterruptedException {
            this.tagName = tagName;
            this.remoteRepository = remoteRepository;
            this.envVars = build.getEnvironment(listener);
            this.gitExe = gitSCM.getGitExe(build.getBuiltOn(), listener);
            this.gitSCM = gitSCM;
            this.workspace = workspace;
            this.listener = listener;
        }

        public String invoke(File ws, VirtualChannel channel) throws IOException, InterruptedException {
            try {
                String escapedTagName = tagName.replace(' ', '_');
                log(listener, String.format("Pushing tag '%s' to '%s'", tagName, remoteRepository));
                GitAPI git = createGitAPI(workspace, gitSCM, envVars, listener, gitExe);
                String pushOutput = git.launchCommand("push", remoteRepository, "refs/tags/" + escapedTagName);
                debuggingLogger.fine(String.format("Push tag command output:%n%s", pushOutput));
                return pushOutput;
            } catch (GitException e) {
                throw new IOException("Failed to push tag: " + e.getMessage());
            }
        }
    }

    private static class PullCallable implements FilePath.FileCallable<String> {
        private final String remoteRepository;
        private final String branch;
        private final GitSCM gitSCM;
        private final TaskListener listener;
        private final FilePath workspace;
        private final EnvVars envVars;
        private final String gitExe;

        public PullCallable(String remoteRepository, String branch, GitSCM gitSCM, TaskListener listener,
                FilePath workspace, AbstractBuild build) throws IOException, InterruptedException {
            this.remoteRepository = remoteRepository;
            this.branch = branch;
            this.envVars = build.getEnvironment(listener);
            this.gitExe = gitSCM.getGitExe(build.getBuiltOn(), listener);
            this.gitSCM = gitSCM;
            this.listener = listener;
            this.workspace = workspace;
        }

        public String invoke(File ws, VirtualChannel channel) throws IOException, InterruptedException {
            try {
                log(listener, "Pulling changes");
                GitAPI git = createGitAPI(workspace, gitSCM, envVars, listener, gitExe);
                String pushOutput = git.launchCommand("pull", remoteRepository, branch);
                debuggingLogger.fine(String.format("Pull command output:%n%s", pushOutput));
                return pushOutput;
            } catch (GitException e) {
                throw new IOException("Failed to pull: " + e.getMessage());
            }
        }
    }

    private static class RevertCallable implements FilePath.FileCallable<String> {
        private final String commitIsh;
        private final GitSCM gitSCM;
        private final TaskListener listener;
        private final FilePath workspace;
        private final EnvVars envVars;
        private final String gitExe;

        public RevertCallable(String commitIsh, GitSCM gitSCM, TaskListener listener,
                FilePath workspace, AbstractBuild build) throws IOException, InterruptedException {
            this.commitIsh = commitIsh;
            this.envVars = build.getEnvironment(listener);
            this.gitExe = gitSCM.getGitExe(build.getBuiltOn(), listener);
            this.gitSCM = gitSCM;
            this.listener = listener;
            this.workspace = workspace;
        }

        public String invoke(File ws, VirtualChannel channel) throws IOException, InterruptedException {
            try {
                log(listener, "Reverting git working copy back to initial commit: " + commitIsh);
                GitAPI git = createGitAPI(workspace, gitSCM, envVars, listener, gitExe);
                String resetOutput = git.launchCommand("reset", "--hard", commitIsh);
                debuggingLogger.fine(String.format("Reset command output:%n%s", resetOutput));
                return resetOutput;
            } catch (GitException e) {
                throw new IOException("Failed to reset working copy: " + e.getMessage());
            }
        }
    }

    private static class DeleteLocalBranchCallable implements FilePath.FileCallable<String> {
        private final String branch;
        private final GitSCM gitSCM;
        private final TaskListener listener;
        private final FilePath workspace;
        private final EnvVars envVars;
        private final String gitExe;

        public DeleteLocalBranchCallable(String branch, GitSCM gitSCM, TaskListener listener,
                FilePath workspace, AbstractBuild build) throws IOException, InterruptedException {
            this.branch = branch;
            this.envVars = build.getEnvironment(listener);
            this.gitExe = gitSCM.getGitExe(build.getBuiltOn(), listener);
            this.workspace = workspace;
            this.listener = listener;
            this.gitSCM = gitSCM;
        }

        public String invoke(File ws, VirtualChannel channel) throws IOException, InterruptedException {
            try {
                log(listener, "Deleting local git branch: " + branch);
                GitAPI git = createGitAPI(workspace, gitSCM, envVars, listener, gitExe);
                String output = git.launchCommand("branch", "-D", branch);
                debuggingLogger.fine(String.format("Delete branch output:%n%s", output));
                return output;
            } catch (GitException e) {
                throw new IOException("Git branch deletion failed: " + e.getMessage());
            }
        }
    }

    private static class DeleteRemoteBranchCallable implements FilePath.FileCallable<String> {
        private final String branch;
        private final String remoteRepository;
        private final GitSCM gitSCM;
        private final TaskListener listener;
        private final FilePath workspace;
        private final EnvVars envVars;
        private final String gitExe;

        public DeleteRemoteBranchCallable(String branch, String remoteRepository, GitSCM gitSCM, TaskListener listener,
                FilePath workspace, AbstractBuild build) throws IOException, InterruptedException {
            this.branch = branch;
            this.remoteRepository = remoteRepository;
            this.envVars = build.getEnvironment(listener);
            this.gitExe = gitSCM.getGitExe(build.getBuiltOn(), listener);
            this.gitSCM = gitSCM;
            this.listener = listener;
            this.workspace = workspace;
        }

        public String invoke(File ws, VirtualChannel channel) throws IOException {
            try {
                log(listener, String.format("Deleting remote branch '%s' on '%s'", branch, remoteRepository));
                GitAPI git = createGitAPI(workspace, gitSCM, envVars, listener, gitExe);
                String pushOutput = git.launchCommand("push", remoteRepository, ":refs/heads/" + branch);
                debuggingLogger.fine(String.format("Push command output:%n%s", pushOutput));
                return pushOutput;
            } catch (GitException e) {
                throw new IOException("Failed to push: " + e.getMessage());
            }
        }
    }

    private static class DeleteLocalTagCallable implements FilePath.FileCallable<String> {
        private final String tag;
        private final GitSCM gitSCM;
        private final TaskListener listener;
        private final FilePath workspace;
        private final EnvVars envVars;
        private final String gitExe;

        public DeleteLocalTagCallable(String tag, GitSCM gitSCM, TaskListener listener, FilePath workspace,
                AbstractBuild build) throws IOException, InterruptedException {
            this.tag = tag;
            this.envVars = build.getEnvironment(listener);
            this.gitExe = gitSCM.getGitExe(build.getBuiltOn(), listener);
            this.gitSCM = gitSCM;
            this.listener = listener;
            this.workspace = workspace;
        }

        public String invoke(File ws, VirtualChannel channel) throws IOException, InterruptedException {
            try {
                log(listener, "Deleting local tag: " + tag);
                GitAPI git = createGitAPI(workspace, gitSCM, envVars, listener, gitExe);
                String output = git.launchCommand("tag", "-d", tag);
                debuggingLogger.fine(String.format("Delete tag output:%n%s", output));
                return output;
            } catch (GitException e) {
                throw new IOException("Git tag deletion failed: " + e.getMessage());
            }
        }
    }

    private static class DeleteRemoteTagCallable implements FilePath.FileCallable<String> {
        private final String tag;
        private final String remoteRepository;
        private final GitSCM gitSCM;
        private final TaskListener listener;
        private final FilePath workspace;
        private final EnvVars envVars;
        private final String gitExe;

        public DeleteRemoteTagCallable(String tag, String remoteRepository, GitSCM gitSCM, TaskListener listener,
                FilePath workspace,
                AbstractBuild build) throws IOException, InterruptedException {
            this.tag = tag;
            this.remoteRepository = remoteRepository;
            this.envVars = build.getEnvironment(listener);
            this.gitExe = gitSCM.getGitExe(build.getBuiltOn(), listener);
            this.gitSCM = gitSCM;
            this.listener = listener;
            this.workspace = workspace;
        }

        public String invoke(File ws, VirtualChannel channel) throws IOException, InterruptedException {
            try {
                log(listener, String.format("Deleting remote tag '%s' from '%s'", tag, remoteRepository));
                GitAPI git = createGitAPI(workspace, gitSCM, envVars, listener, gitExe);
                String output = git.launchCommand("push", remoteRepository, ":refs/tags/" + tag);
                debuggingLogger.fine(String.format("Delete tag output:%n%s", output));
                return output;
            } catch (GitException e) {
                throw new IOException("Git tag deletion failed: " + e.getMessage());
            }
        }
    }
}
