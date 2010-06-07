package org.jfrog.hudson;

import hudson.model.AbstractBuild;
import hudson.model.Action;

/**
 * Result of the redeploy publisher. Currently only a link to Artifactory build info.
 *
 * @author Yossi Shaul
 */
public class BuildInfoResultAction implements Action {
    private ArtifactoryRedeployPublisher artifactoryRedeployPublisher;
    private final AbstractBuild build;

    public BuildInfoResultAction(ArtifactoryRedeployPublisher artifactoryRedeployPublisher, AbstractBuild build) {
        this.artifactoryRedeployPublisher = artifactoryRedeployPublisher;
        this.build = build;
    }

    public String getIconFileName() {
        return "/plugin/artifactory/images/artifactory-icon.png";
    }

    public String getDisplayName() {
        return "Artifactory";
    }

    public String getUrlName() {
        return artifactoryRedeployPublisher.getArtifactoryName() + "/webapp/builds/"
                + build.getParent().getDisplayName() + "/"
                + build.getNumber();
    }
}
