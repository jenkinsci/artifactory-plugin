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
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.jfrog.hudson.release.scm.AbstractScmManager;

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
        GitSCM gitSCM = getJenkinsScm();
        return workspace
                .act(new CurrentCommitCallable(gitSCM, getWorkingDirectory(gitSCM, workspace), build, buildListener));
    }

    public String checkoutBranch(final String branch, final boolean create) throws IOException, InterruptedException {
        FilePath workspace = build.getWorkspace();
        GitSCM gitSCM = getJenkinsScm();
        FilePath workingDirectory = getWorkingDirectory(gitSCM, workspace);
        return workspace
                .act(new CheckoutBranchCallable(create, branch, gitSCM, buildListener, build, workingDirectory));
    }

    public void commitWorkingCopy(final String commitMessage) throws IOException, InterruptedException {
        FilePath workspace = build.getWorkspace();
        GitSCM gitSCM = getJenkinsScm();
        FilePath workingDirectory = getWorkingDirectory(gitSCM, workspace);
        workspace.act(new CommitWorkingCopyCallable(commitMessage, gitSCM, buildListener, build,
                workingDirectory));
    }

    public void createTag(final String tagName, final String commitMessage)
            throws IOException, InterruptedException {
        FilePath workspace = build.getWorkspace();
        GitSCM gitSCM = getJenkinsScm();
        FilePath workingDirectory = getWorkingDirectory(gitSCM, workspace);
        workspace.act(new CreateTagCallable(tagName, commitMessage, gitSCM, buildListener, build, workingDirectory));
    }

    public String push(final String remoteRepository, final String branch) throws IOException, InterruptedException {
        FilePath workspace = build.getWorkspace();
        GitSCM gitSCM = getJenkinsScm();
        FilePath workingDirectory = getWorkingDirectory(gitSCM, workspace);
        return workspace
                .act(new PushCallable(branch, remoteRepository, gitSCM, buildListener, build, workingDirectory));
    }

    public String pushTag(final String remoteRepository, final String tagName)
            throws IOException, InterruptedException {
        FilePath workspace = build.getWorkspace();
        GitSCM gitSCM = getJenkinsScm();
        FilePath directory = getWorkingDirectory(gitSCM, workspace);
        return workspace
                .act(new PushTagCallable(tagName, remoteRepository, gitSCM, buildListener, build, directory));
    }

    public String pull(final String remoteRepository, final String branch) throws IOException, InterruptedException {
        FilePath workspace = build.getWorkspace();
        FilePath directory = getWorkingDirectory(getJenkinsScm(), workspace);
        return workspace
                .act(new PullCallable(remoteRepository, branch, getJenkinsScm(), buildListener, build,
                        directory));
    }

    public void revertWorkingCopy(final String commitIsh) throws IOException, InterruptedException {
        FilePath workspace = build.getWorkspace();
        FilePath directory = getWorkingDirectory(getJenkinsScm(), workspace);
        workspace.act(new RevertCallable(commitIsh, getJenkinsScm(), buildListener, build, directory));
    }

    public String deleteLocalBranch(final String branch) throws IOException, InterruptedException {
        FilePath workspace = build.getWorkspace();
        FilePath directory = getWorkingDirectory(getJenkinsScm(), workspace);
        return workspace.act(new DeleteLocalBranchCallable(branch, getJenkinsScm(), buildListener, build, directory));
    }

    public String deleteRemoteBranch(final String remoteRepository, final String branch)
            throws IOException, InterruptedException {
        FilePath workspace = build.getWorkspace();
        FilePath directory = getWorkingDirectory(getJenkinsScm(), workspace);
        return workspace
                .act(new DeleteRemoteBranchCallable(branch, remoteRepository, getJenkinsScm(), buildListener,
                        build, directory));
    }

    public String deleteLocalTag(final String tag) throws IOException, InterruptedException {
        FilePath workspace = build.getWorkspace();
        FilePath directory = getWorkingDirectory(getJenkinsScm(), workspace);
        return workspace.act(new DeleteLocalTagCallable(tag, getJenkinsScm(), buildListener, build,
                directory));
    }

    public String deleteRemoteTag(final String remoteRepository, final String tag)
            throws IOException, InterruptedException {
        FilePath workspace = build.getWorkspace();
        FilePath directory = getWorkingDirectory(getJenkinsScm(), workspace);
        return workspace.act(
                new DeleteRemoteTagCallable(tag, remoteRepository, getJenkinsScm(), buildListener, build, directory));
    }

    public String getRemoteUrl() {
        RemoteConfig remoteConfig = getJenkinsScm().getRepositories().get(0);
        URIish uri = remoteConfig.getURIs().get(0);
        return uri.toPrivateString();
    }

    private static GitAPI createGitAPI(EnvVars gitEnvironment,
                                       TaskListener buildListener, String gitExe, FilePath workingCopy, String confName, String confEmail)
            throws IOException {
        if (StringUtils.isNotBlank(confName)) {
            gitEnvironment.put("GIT_COMMITTER_NAME", confName);
            gitEnvironment.put("GIT_AUTHOR_NAME", confName);
        }
        if (StringUtils.isNotBlank(confEmail)) {
            gitEnvironment.put("GIT_COMMITTER_EMAIL", confEmail);
            gitEnvironment.put("GIT_AUTHOR_EMAIL", confEmail);
        }
        GitAPI git = new GitAPI(gitExe, new File(workingCopy.getName()), buildListener, new EnvVars());
        return git;
    }

    private static FilePath getWorkingDirectory(GitSCM gitSCM, FilePath ws) throws IOException {
        // working directory might be relative to the workspace
        String relativeTargetDir = gitSCM.getRelativeTargetDir() == null ? "" : gitSCM.getRelativeTargetDir();
        return new FilePath(ws, relativeTargetDir);
    }

    private static class CurrentCommitCallable implements FilePath.FileCallable<String> {
        private final FilePath workingCopy;
        private final TaskListener listener;
        private final EnvVars envVars;
        private final String gitExe;
        private final String confName;
        private final String confEmail;

        private CurrentCommitCallable(GitSCM gitSCM, FilePath workingCopy, AbstractBuild build,
                                      TaskListener listener) throws IOException, InterruptedException {
            this.workingCopy = workingCopy;
            this.listener = listener;
            this.confName = gitSCM.getGitConfigNameToUse();
            this.confEmail = gitSCM.getGitConfigEmailToUse();
            this.envVars = build.getEnvironment(listener);
            this.gitExe = gitSCM.getGitExe(build.getBuiltOn(), listener);
        }

        public String invoke(File ws, VirtualChannel channel) throws IOException, InterruptedException {
            try {
                GitAPI git = createGitAPI(envVars, listener, gitExe, workingCopy, confName, confEmail);
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
        private final TaskListener listener;
        private final EnvVars envVars;
        private final String gitExe;
        private final FilePath workingCopy;
        private final String confName;
        private final String email;

        public CheckoutBranchCallable(boolean create, String branch, GitSCM gitSCM, TaskListener listener,
                                      AbstractBuild build, FilePath workingCopy) throws IOException, InterruptedException {
            this.create = create;
            this.branch = branch;
            this.listener = listener;
            this.workingCopy = workingCopy;
            this.confName = gitSCM.getGitConfigNameToUse();
            this.email = gitSCM.getGitConfigEmailToUse();
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
                GitAPI git = createGitAPI(envVars, listener, gitExe, workingCopy, confName, email);
                String checkoutResult = git.launchCommand(args);
                debuggingLogger.fine(String.format("Checkout result: %s", checkoutResult));
                return checkoutResult;
            } catch (GitException e) {
                throw new IOException("Failed checkout branch: " + e.getMessage());
            }
        }
    }

    private static class CommitWorkingCopyCallable implements FilePath.FileCallable<String> {
        private final TaskListener listener;
        private final EnvVars envVars;
        private final String gitExe;
        private final String commitMessage;
        private final FilePath workingCopy;
        private final String confName;
        private final String email;

        public CommitWorkingCopyCallable(String commitMessage, GitSCM gitSCM, TaskListener listener,
                                         AbstractBuild build, FilePath workingCopy) throws IOException, InterruptedException {
            this.commitMessage = commitMessage;
            this.workingCopy = workingCopy;
            this.confName = gitSCM.getGitConfigNameToUse();
            this.email = gitSCM.getGitConfigEmailToUse();
            this.envVars = build.getEnvironment(listener);
            this.gitExe = gitSCM.getGitExe(build.getBuiltOn(), listener);
            this.listener = listener;
        }

        public String invoke(File ws, VirtualChannel channel) throws IOException, InterruptedException {
            try {
                // commit all the modified files
                GitAPI git = createGitAPI(envVars, listener, gitExe, workingCopy, confName, email);
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
        private final FilePath workingCopy;
        private final String confName;
        private final String email;
        private final TaskListener listener;
        private final EnvVars envVars;
        private final String gitExe;

        public CreateTagCallable(String tagName, String commitMessage, GitSCM gitSCM, TaskListener listener,
                                 AbstractBuild build, FilePath workingCopy) throws IOException, InterruptedException {
            this.tagName = tagName;
            this.commitMessage = commitMessage;
            this.workingCopy = workingCopy;
            this.confName = gitSCM.getGitConfigNameToUse();
            this.email = gitSCM.getGitConfigEmailToUse();
            this.envVars = build.getEnvironment(listener);
            this.gitExe = gitSCM.getGitExe(build.getBuiltOn(), listener);
            this.listener = listener;
        }

        public String invoke(File ws, VirtualChannel channel) throws IOException, InterruptedException {
            try {
                String escapedTagName = tagName.replace(' ', '_');
                log(listener, String.format("Creating tag '%s'", escapedTagName));
                GitAPI git = createGitAPI(envVars, listener, gitExe, workingCopy, confName, email);
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
        private final FilePath workingCopy;
        private final String confName;
        private final String email;
        private final TaskListener listener;
        private final EnvVars envVars;
        private final String gitExe;

        public PushCallable(String branch, String remoteRepository, GitSCM gitSCM, TaskListener listener,
                            AbstractBuild build, FilePath workingCopy) throws IOException, InterruptedException {
            this.branch = branch;
            this.remoteRepository = remoteRepository;
            this.workingCopy = workingCopy;
            this.confName = gitSCM.getGitConfigNameToUse();
            this.email = gitSCM.getGitConfigEmailToUse();
            this.envVars = build.getEnvironment(listener);
            this.gitExe = gitSCM.getGitExe(build.getBuiltOn(), listener);
            this.listener = listener;
        }

        public String invoke(File ws, VirtualChannel channel) throws IOException, InterruptedException {
            try {
                log(listener, String.format("Pushing branch '%s' to '%s'", branch, remoteRepository));
                GitAPI git = createGitAPI(envVars, listener, gitExe, workingCopy, confName, email);
                String pushOutput = git.launchCommand("push", remoteRepository, "refs/heads/" + branch);
                debuggingLogger.fine(String.format("Push command output:%n%s", pushOutput));
                return pushOutput;
            } catch (GitException e) {
                throw new IOException("Failed to push: " + e.getMessage());
            }
        }
    }

    private static class PushTagCallable implements FilePath.FileCallable<String> {
        private final TaskListener listener;
        private final EnvVars envVars;
        private final String gitExe;
        private final String tagName;
        private final String remoteRepository;
        private final FilePath workingCopy;
        private final String confName;
        private final String email;


        public PushTagCallable(String tagName, String remoteRepository, GitSCM gitSCM, TaskListener listener,
                               AbstractBuild build, FilePath workingCopy) throws IOException, InterruptedException {
            this.tagName = tagName;
            this.remoteRepository = remoteRepository;
            this.workingCopy = workingCopy;
            this.confName = gitSCM.getGitConfigNameToUse();
            this.email = gitSCM.getGitConfigEmailToUse();
            this.envVars = build.getEnvironment(listener);
            this.gitExe = gitSCM.getGitExe(build.getBuiltOn(), listener);
            this.listener = listener;
        }

        public String invoke(File ws, VirtualChannel channel) throws IOException, InterruptedException {
            try {
                String escapedTagName = tagName.replace(' ', '_');
                log(listener, String.format("Pushing tag '%s' to '%s'", tagName, remoteRepository));
                GitAPI git = createGitAPI(envVars, listener, gitExe, workingCopy, confName, email);
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
        private final TaskListener listener;
        private final EnvVars envVars;
        private final String gitExe;
        private final FilePath workingCopy;
        private final String confName;
        private final String email;

        public PullCallable(String remoteRepository, String branch, GitSCM gitSCM, TaskListener listener,
                            AbstractBuild build, FilePath workingCopy) throws IOException, InterruptedException {
            this.remoteRepository = remoteRepository;
            this.branch = branch;
            this.workingCopy = workingCopy;
            this.envVars = build.getEnvironment(listener);
            this.gitExe = gitSCM.getGitExe(build.getBuiltOn(), listener);
            this.listener = listener;
            this.confName = gitSCM.getGitConfigNameToUse();
            this.email = gitSCM.getGitConfigEmailToUse();
        }

        public String invoke(File ws, VirtualChannel channel) throws IOException, InterruptedException {
            try {
                log(listener, "Pulling changes");
                GitAPI git = createGitAPI(envVars, listener, gitExe, workingCopy, confName, email);
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
        private final TaskListener listener;
        private final EnvVars envVars;
        private final String gitExe;
        private final FilePath workingCopy;
        private final String confName;
        private final String email;

        public RevertCallable(String commitIsh, GitSCM gitSCM, TaskListener listener,
                              AbstractBuild build, FilePath workingCopy) throws IOException, InterruptedException {
            this.commitIsh = commitIsh;
            this.workingCopy = workingCopy;
            this.envVars = build.getEnvironment(listener);
            this.gitExe = gitSCM.getGitExe(build.getBuiltOn(), listener);
            this.listener = listener;
            this.confName = gitSCM.getGitConfigNameToUse();
            this.email = gitSCM.getGitConfigEmailToUse();
        }

        public String invoke(File ws, VirtualChannel channel) throws IOException, InterruptedException {
            try {
                log(listener, "Reverting git working copy back to initial commit: " + commitIsh);
                GitAPI git = createGitAPI(envVars, listener, gitExe, workingCopy, confName, email);
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
        private final TaskListener listener;
        private final EnvVars envVars;
        private final String gitExe;
        private final FilePath workingCopy;
        private final String confName;
        private final String email;

        public DeleteLocalBranchCallable(String branch, GitSCM gitSCM, TaskListener listener,
                                         AbstractBuild build, FilePath workingCopy) throws IOException, InterruptedException {
            this.branch = branch;
            this.workingCopy = workingCopy;
            this.envVars = build.getEnvironment(listener);
            this.gitExe = gitSCM.getGitExe(build.getBuiltOn(), listener);
            this.listener = listener;
            this.confName = gitSCM.getGitConfigNameToUse();
            this.email = gitSCM.getGitConfigEmailToUse();
        }

        public String invoke(File ws, VirtualChannel channel) throws IOException, InterruptedException {
            try {
                log(listener, "Deleting local git branch: " + branch);
                GitAPI git = createGitAPI(envVars, listener, gitExe, workingCopy, confName, email);
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
        private final TaskListener listener;
        private final EnvVars envVars;
        private final String gitExe;
        private final FilePath workingCopy;
        private final String confName;
        private final String email;


        public DeleteRemoteBranchCallable(String branch, String remoteRepository, GitSCM gitSCM, TaskListener listener,
                                          AbstractBuild build, FilePath workingCopy) throws IOException, InterruptedException {
            this.branch = branch;
            this.remoteRepository = remoteRepository;
            this.workingCopy = workingCopy;
            this.envVars = build.getEnvironment(listener);
            this.gitExe = gitSCM.getGitExe(build.getBuiltOn(), listener);
            this.listener = listener;
            this.confName = gitSCM.getGitConfigNameToUse();
            this.email = gitSCM.getGitConfigEmailToUse();
        }

        public String invoke(File ws, VirtualChannel channel) throws IOException {
            try {
                log(listener, String.format("Deleting remote branch '%s' on '%s'", branch, remoteRepository));
                GitAPI git = createGitAPI(envVars, listener, gitExe, workingCopy, confName, email);
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
        private final TaskListener listener;
        private final EnvVars envVars;
        private final String gitExe;
        private final FilePath workingCopy;
        private final String confName;
        private final String email;

        public DeleteLocalTagCallable(String tag, GitSCM gitSCM, TaskListener listener,
                                      AbstractBuild build, FilePath workingCopy) throws IOException, InterruptedException {
            this.tag = tag;
            this.workingCopy = workingCopy;
            this.envVars = build.getEnvironment(listener);
            this.gitExe = gitSCM.getGitExe(build.getBuiltOn(), listener);
            this.listener = listener;
            this.confName = gitSCM.getGitConfigNameToUse();
            this.email = gitSCM.getGitConfigEmailToUse();
        }

        public String invoke(File ws, VirtualChannel channel) throws IOException, InterruptedException {
            try {
                log(listener, "Deleting local tag: " + tag);
                GitAPI git = createGitAPI(envVars, listener, gitExe, workingCopy, confName, email);
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
        private final TaskListener listener;
        private final EnvVars envVars;
        private final String gitExe;
        private final FilePath workingCopy;
        private final String confName;
        private final String email;

        public DeleteRemoteTagCallable(String tag, String remoteRepository, GitSCM gitSCM, TaskListener listener,
                                       AbstractBuild build, FilePath workingCopy) throws IOException, InterruptedException {
            this.tag = tag;
            this.remoteRepository = remoteRepository;
            this.workingCopy = workingCopy;
            this.envVars = build.getEnvironment(listener);
            this.gitExe = gitSCM.getGitExe(build.getBuiltOn(), listener);
            this.listener = listener;
            this.confName = gitSCM.getGitConfigNameToUse();
            this.email = gitSCM.getGitConfigEmailToUse();
        }

        public String invoke(File ws, VirtualChannel channel) throws IOException, InterruptedException {
            try {
                log(listener, String.format("Deleting remote tag '%s' from '%s'", tag, remoteRepository));
                GitAPI git = createGitAPI(envVars, listener, gitExe, workingCopy, confName, email);
                String output = git.launchCommand("push", remoteRepository, ":refs/tags/" + tag);
                debuggingLogger.fine(String.format("Delete tag output:%n%s", output));
                return output;
            } catch (GitException e) {
                throw new IOException("Git tag deletion failed: " + e.getMessage());
            }
        }
    }
}
