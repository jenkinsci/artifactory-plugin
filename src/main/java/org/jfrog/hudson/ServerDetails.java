package org.jfrog.hudson;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Represents a configured artifactory instance.
 */
public class ServerDetails {
    public final String artifactoryName;
    public final String repositoryKey;

    @DataBoundConstructor
    public ServerDetails(String artifactoryName, String repositoryKey) {
        this.artifactoryName = artifactoryName;
        this.repositoryKey = repositoryKey;
    }
}
