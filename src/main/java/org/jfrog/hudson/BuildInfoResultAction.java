package org.jfrog.hudson;

import hudson.model.AbstractBuild;
import hudson.model.Action;

/**
 * Result of the redeploy publisher. Currently only a link to Artifactory build info.
 *
 * @author Yossi Shaul
 */
public class BuildInfoResultAction implements Action {

    private final String url;

    /**
     * @deprecated Only here to keep compatibility with version 1.0.7 and below (part of the xstream de-serialization)
     */
    @Deprecated
    private transient ArtifactoryRedeployPublisher artifactoryRedeployPublisher;
    /**
     * @deprecated Only here to keep compatibility with version 1.0.7 and below (part of the xstream de-serialization)
     */
    @Deprecated
    private transient AbstractBuild build;

    public BuildInfoResultAction(ArtifactoryRedeployPublisher artifactoryRedeployPublisher, AbstractBuild build) {
        url = generateUrl(artifactoryRedeployPublisher, build);
    }

    public String getIconFileName() {
        return "/plugin/artifactory/images/artifactory-icon.png";
    }

    public String getDisplayName() {
        return "Artifactory";
    }

    public String getUrlName() {
        // for backward compatibility if url is empty calculate it from the old structs
        if (url == null && artifactoryRedeployPublisher != null && build != null) {
            return generateUrl(artifactoryRedeployPublisher, build);
        } else {
            return url;
        }
    }

    private String generateUrl(ArtifactoryRedeployPublisher artifactoryRedeployPublisher, AbstractBuild build) {
        return artifactoryRedeployPublisher.getArtifactoryName() + "/webapp/builds/"
                + build.getParent().getDisplayName() + "/"
                + build.getNumber();
    }
}