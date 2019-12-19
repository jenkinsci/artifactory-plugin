package org.jfrog.hudson.pipeline.integration;

/**
 * @author yahavi
 */
enum TestRepository {
    LOCAL_REPO1("jenkins-artifactory-tests-local-1"),
    LOCAL_REPO2("jenkins-artifactory-tests-local-2"),
    JCENTER_REMOTE_REPO("jenkins-artifactory-tests-jcenter"),
    NPM_LOCAL("jenkins-artifactory-tests-npm-local"),
    NPM_REMOTE("jenkins-artifactory-tests-npm-remote");

    private String repoName;

    TestRepository(String repoName) {
        this.repoName = repoName;
    }

    public String getRepoName() {
        return repoName;
    }

    @Override
    public String toString() {
        return getRepoName();
    }
}
