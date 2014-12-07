package org.jfrog.hudson.release.gradle;

import hudson.model.AbstractProject;
import org.jfrog.hudson.ArtifactoryPlugin;

/**
 * This class is used for managing the Artifactory Release Staging functionality for Gradle projects from Jenkins UI.
 */
public class GradleReleaseAction extends BaseGradleReleaseAction {
    public GradleReleaseAction(AbstractProject<?, ?> project) {
        super(project);
    }

    public String getIconFileName() {
        if (project.hasPermission(ArtifactoryPlugin.RELEASE)) {
            return "/plugin/artifactory/images/artifactory-release.png";
        }

        // return null to hide the action (doSubmit will also perform permission check if someone tries direct link)
        return null;
    }
}
