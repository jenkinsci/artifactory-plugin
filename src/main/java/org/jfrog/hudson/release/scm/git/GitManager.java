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
        return workspace.act(new CurrentCommitCallable(createGitAPI(workspace)));
    }

    public String checkoutBranch(final String branch, final boolean create) throws IOException, InterruptedException {
        FilePath workspace = build.getWorkspace();
        return workspace.act(new CheckoutBranchCallable(createGitAPI(workspace), create, branch));
    }

    public void commitWorkingCopy(final String commitMessage) throws IOException, InterruptedException {
        FilePath workspace = build.getWorkspace();
        workspace.act(new CommitWorkingCopyCallable(createGitAPI(workspace), commitMessage));
    }

    public void createTag(final String tagName, final String commitMessage)
            throws IOException, InterruptedException {
        build.getWorkspace()
                .act(new CreateTagCallable(createGitAPI(build.getWorkspace()), tagName, commitMessage, buildListener));
    }

    public String push(final String remoteRepository, final String branch) throws IOException, InterruptedException {
        FilePath workspace = build.getWorkspace();
        return workspace.act(new PushCallable(createGitAPI(workspace), branch, remoteRepository, buildListener));
    }

    public String pushTag(final String remoteRepository, final String tagName)
            throws IOException, InterruptedException {
        FilePath workspace = build.getWorkspace();
        return workspace.act(new PushTagCallable(createGitAPI(workspace), tagName, remoteRepository, buildListener));
    }

    public String pull(final String remoteRepository, final String branch) throws IOException, InterruptedException {
        FilePath workspace = build.getWorkspace();
        return workspace.act(new PullCallable(createGitAPI(workspace), remoteRepository, branch, buildListener));
    }

    public void revertWorkingCopy(final String commitIsh) throws IOException, InterruptedException {
        FilePath workspace = build.getWorkspace();
        workspace.act(new RevertCallable(createGitAPI(workspace), commitIsh, buildListener));
    }

    public String deleteLocalBranch(final String branch) throws IOException, InterruptedException {
        FilePath workspace = build.getWorkspace();
        return workspace.act(new DeleteLocalBranchCallable(createGitAPI(workspace), branch, buildListener));
    }

    public String deleteRemoteBranch(final String remoteRepository, final String branch)
            throws IOException, InterruptedException {
        FilePath workspace = build.getWorkspace();
        return workspace
                .act(new DeleteRemoteBranchCallable(createGitAPI(workspace), branch, remoteRepository, buildListener));
    }

    public String deleteLocalTag(final String tag) throws IOException, InterruptedException {
        FilePath workspace = build.getWorkspace();
        return workspace.act(new DeleteLocalTagCallable(createGitAPI(workspace), tag, buildListener));
    }

    public String deleteRemoteTag(final String remoteRepository, final String tag)
            throws IOException, InterruptedException {
        FilePath workspace = build.getWorkspace();
        return workspace
                .act(new DeleteRemoteTagCallable(createGitAPI(workspace), tag, remoteRepository, buildListener));
    }

    public String getRemoteUrl() {
        RemoteConfig remoteConfig = getHudsonScm().getRepositories().get(0);
        URIish uri = remoteConfig.getURIs().get(0);
        return uri.toPrivateString();
    }

    private GitAPI createGitAPI(FilePath workspace) throws IOException {
        GitSCM gitSCM = getHudsonScm();
        File workingCopy = getWorkingDirectory(new File(workspace.getRemote()));
        String gitExe = gitSCM.getGitExe(build.getBuiltOn(), buildListener);

        EnvVars gitEnvironment;
        try {
            gitEnvironment = build.getEnvironment(buildListener);
        } catch (InterruptedException e) {
            throw new RuntimeException("Failed to get environment", e);
        }
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

    private static class CurrentCommitCallable implements FilePath.FileCallable<String> {
        private final GitAPI git;

        private CurrentCommitCallable(GitAPI git) {
            this.git = git;
        }

        public String invoke(File ws, VirtualChannel channel) throws IOException, InterruptedException {
            try {
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
        private GitAPI git;

        public CheckoutBranchCallable(GitAPI git, boolean create, String branch) {
            this.create = create;
            this.branch = branch;
            this.git = git;
        }

        public String invoke(File ws, VirtualChannel channel) throws IOException, InterruptedException {
            try {
                // commit all the modified files
                ArgumentListBuilder args = new ArgumentListBuilder("checkout");
                if (create) {
                    args.add("-b"); // force create new branch
                }
                args.add(branch);
                String checkoutResult = git.launchCommand(args);
                debuggingLogger.fine(String.format("Checkout result: %s", checkoutResult));
                return checkoutResult;
            } catch (GitException e) {
                throw new IOException("Failed checkout branch: " + e.getMessage());
            }
        }
    }

    private static class CommitWorkingCopyCallable implements FilePath.FileCallable<String> {
        private GitAPI git;
        private final String commitMessage;

        public CommitWorkingCopyCallable(GitAPI git, String commitMessage) {
            this.commitMessage = commitMessage;
            this.git = git;
        }

        public String invoke(File ws, VirtualChannel channel) throws IOException, InterruptedException {
            try {
                // commit all the modified files
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
        private GitAPI git;
        private final TaskListener listener;

        public CreateTagCallable(GitAPI git, String tagName, String commitMessage, TaskListener listener) {
            this.tagName = tagName;
            this.commitMessage = commitMessage;
            this.git = git;
            this.listener = listener;
        }

        public String invoke(File ws, VirtualChannel channel) throws IOException, InterruptedException {
            try {
                String escapedTagName = tagName.replace(' ', '_');
                log(listener, String.format("Creating tag '%s'", escapedTagName));
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
        private GitAPI git;
        private TaskListener taskListener;

        public PushCallable(GitAPI git, String branch, String remoteRepository, TaskListener taskListener) {
            this.branch = branch;
            this.remoteRepository = remoteRepository;
            this.git = git;
            this.taskListener = taskListener;
        }

        public String invoke(File workspace, VirtualChannel channel) throws IOException, InterruptedException {
            try {
                log(taskListener, String.format("Pushing branch '%s' to '%s'", branch, remoteRepository));
                String pushOutput = git.launchCommand("push", remoteRepository, "refs/heads/" + branch);
                debuggingLogger.fine(String.format("Push command output:%n%s", pushOutput));
                return pushOutput;
            } catch (GitException e) {
                throw new IOException("Failed to push: " + e.getMessage());
            }
        }
    }

    private static class PushTagCallable implements FilePath.FileCallable<String> {
        private final GitAPI git;
        private final String tagName;
        private final String remoteRepository;
        private final TaskListener taskListener;

        public PushTagCallable(GitAPI git, String tagName, String remoteRepository, TaskListener taskListener) {
            this.git = git;
            this.tagName = tagName;
            this.remoteRepository = remoteRepository;
            this.taskListener = taskListener;
        }

        public String invoke(File workspace, VirtualChannel channel) throws IOException, InterruptedException {
            try {
                String escapedTagName = tagName.replace(' ', '_');
                log(taskListener, String.format("Pushing tag '%s' to '%s'", tagName, remoteRepository));
                String pushOutput = git.launchCommand("push", remoteRepository, "refs/tags/" + escapedTagName);
                debuggingLogger.fine(String.format("Push tag command output:%n%s", pushOutput));
                return pushOutput;
            } catch (GitException e) {
                throw new IOException("Failed to push tag: " + e.getMessage());
            }
        }
    }

    private static class PullCallable implements FilePath.FileCallable<String> {
        private final GitAPI git;
        private final String remoteRepository;
        private final String branch;
        private final TaskListener taskListener;

        public PullCallable(GitAPI git, String remoteRepository, String branch, TaskListener taskListener) {
            this.git = git;
            this.remoteRepository = remoteRepository;
            this.branch = branch;
            this.taskListener = taskListener;
        }

        public String invoke(File workspace, VirtualChannel channel) throws IOException, InterruptedException {
            try {
                log(taskListener, "Pulling changes");
                String pushOutput = git.launchCommand("pull", remoteRepository, branch);
                debuggingLogger.fine(String.format("Pull command output:%n%s", pushOutput));
                return pushOutput;
            } catch (GitException e) {
                throw new IOException("Failed to pull: " + e.getMessage());
            }
        }
    }

    private static class RevertCallable implements FilePath.FileCallable<String> {
        private final GitAPI git;
        private final String commitIsh;
        private final TaskListener taskListener;

        public RevertCallable(GitAPI git, String commitIsh, TaskListener taskListener) {
            this.git = git;
            this.commitIsh = commitIsh;
            this.taskListener = taskListener;
        }

        public String invoke(File workspace, VirtualChannel channel) throws IOException, InterruptedException {
            try {
                log(taskListener, "Reverting git working copy back to initial commit: " + commitIsh);
                String resetOutput = git.launchCommand("reset", "--hard", commitIsh);
                debuggingLogger.fine(String.format("Reset command output:%n%s", resetOutput));
                return resetOutput;
            } catch (GitException e) {
                throw new IOException("Failed to reset working copy: " + e.getMessage());
            }
        }
    }

    private static class DeleteLocalBranchCallable implements FilePath.FileCallable<String> {
        private final GitAPI git;
        private final String branch;
        private final TaskListener taskListener;

        public DeleteLocalBranchCallable(GitAPI git, String branch, TaskListener taskListener) {
            this.git = git;
            this.branch = branch;
            this.taskListener = taskListener;
        }

        public String invoke(File workspace, VirtualChannel channel) throws IOException, InterruptedException {
            try {
                log(taskListener, "Deleting local git branch: " + branch);
                String output = git.launchCommand("branch", "-D", branch);
                debuggingLogger.fine(String.format("Delete branch output:%n%s", output));
                return output;
            } catch (GitException e) {
                throw new IOException("Git branch deletion failed: " + e.getMessage());
            }
        }
    }

    private static class DeleteRemoteBranchCallable implements FilePath.FileCallable<String> {
        private final GitAPI git;
        private final String branch;
        private final String remoteRepository;
        private final TaskListener taskListener;

        public DeleteRemoteBranchCallable(GitAPI git, String branch, String remoteRepository,
                TaskListener taskListener) {
            this.git = git;
            this.branch = branch;
            this.remoteRepository = remoteRepository;
            this.taskListener = taskListener;
        }

        public String invoke(File workspace, VirtualChannel channel) throws IOException {
            try {
                log(taskListener, String.format("Deleting remote branch '%s' on '%s'", branch, remoteRepository));
                String pushOutput = git.launchCommand("push", remoteRepository, ":refs/heads/" + branch);
                debuggingLogger.fine(String.format("Push command output:%n%s", pushOutput));
                return pushOutput;
            } catch (GitException e) {
                throw new IOException("Failed to push: " + e.getMessage());
            }
        }
    }

    private static class DeleteLocalTagCallable implements FilePath.FileCallable<String> {
        private final GitAPI git;
        private final String tag;
        private final TaskListener taskListener;

        public DeleteLocalTagCallable(GitAPI git, String tag, TaskListener taskListener) {
            this.git = git;
            this.tag = tag;
            this.taskListener = taskListener;
        }

        public String invoke(File workspace, VirtualChannel channel) throws IOException, InterruptedException {
            try {
                log(taskListener, "Deleting local tag: " + tag);
                String output = git.launchCommand("tag", "-d", tag);
                debuggingLogger.fine(String.format("Delete tag output:%n%s", output));
                return output;
            } catch (GitException e) {
                throw new IOException("Git tag deletion failed: " + e.getMessage());
            }
        }
    }

    private static class DeleteRemoteTagCallable implements FilePath.FileCallable<String> {
        private final GitAPI git;
        private final String tag;
        private final String remoteRepository;
        private final TaskListener taskListener;

        public DeleteRemoteTagCallable(GitAPI git, String tag, String remoteRepository, TaskListener taskListener) {
            this.git = git;
            this.tag = tag;
            this.remoteRepository = remoteRepository;
            this.taskListener = taskListener;
        }

        public String invoke(File workspace, VirtualChannel channel) throws IOException, InterruptedException {
            try {
                log(taskListener, String.format("Deleting remote tag '%s' from '%s'", tag, remoteRepository));
                String output = git.launchCommand("push", remoteRepository, ":refs/tags/" + tag);
                debuggingLogger.fine(String.format("Delete tag output:%n%s", output));
                return output;
            } catch (GitException e) {
                throw new IOException("Git tag deletion failed: " + e.getMessage());
            }
        }
    }
}
