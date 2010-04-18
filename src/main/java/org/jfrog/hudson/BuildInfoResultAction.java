package org.jfrog.hudson;

import hudson.model.AbstractBuild;
import hudson.model.Action;

/**
 * Result of the redeploy publisher. Currently only a link to Artifactory build info.
 *
 * @author Yossi Shaul
 */
public class BuildInfoResultAction implements Action {
    private ServerDetails serverDetails;
    private final AbstractBuild build;

    public BuildInfoResultAction(ServerDetails serverDetails, AbstractBuild build) {
        this.serverDetails = serverDetails;
        this.build = build;
    }


    public String getIconFileName() {
        return "/plugin/artifactory/images/artifactory-icon.png";
    }

    public String getDisplayName() {
        return "Artifactory";
    }

    public String getUrlName() {
        return serverDetails.artifactoryName + "/webapp/builds/"
                + build.getParent().getDisplayName() + "/"
                + build.getNumber();
    }
}
