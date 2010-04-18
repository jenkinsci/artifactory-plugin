package org.jfrog.hudson;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Represents a configured artifactory instance.
 */
public class ServerDetails {
    public final String artifactoryName;
    public final String repositoryKey;
    public final String downloadRepositoryKey;

    @DataBoundConstructor
    public ServerDetails(String artifactoryName, String repositoryKey, String downloadRepositoryKey) {
        this.artifactoryName = artifactoryName;
        this.repositoryKey = repositoryKey;
        this.downloadRepositoryKey = downloadRepositoryKey;
    }
}
