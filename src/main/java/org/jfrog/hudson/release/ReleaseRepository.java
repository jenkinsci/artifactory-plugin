package org.jfrog.hudson.release;

import org.eclipse.jgit.transport.URIish;

/**
 * @author Lior Hasson
 */
public class ReleaseRepository {

    private String gitUri;
    private String repositoryName;
    private boolean targetRepoUri;
    private String targetRepoPrivateUri;

    public ReleaseRepository(URIish gitUri, String repoName) {
        this.gitUri = gitUri.toString();
        this.targetRepoPrivateUri = gitUri.toPrivateASCIIString();
        this.repositoryName = repoName;
        targetRepoUri = false;
    }

    public ReleaseRepository(String gitUri, String repoName) {
        this.gitUri = gitUri;
        this.targetRepoPrivateUri = gitUri;
        this.repositoryName = repoName;
        targetRepoUri = true;
    }

    public String getGitUri() {
        return gitUri;
    }

    public String getRepositoryName() {
        return repositoryName;
    }

    public boolean isTargetRepoUri() {
        return targetRepoUri;
    }

    public String getTargetRepoPrivateUri() {
        return targetRepoPrivateUri;
    }
}
