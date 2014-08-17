
package org.jfrog.hudson.release.gradle;

import hudson.model.FreeStyleProject;
import org.jfrog.hudson.ArtifactoryPlugin;

public class GradleReleaseAction extends BaseGradleReleaseAction {
    public GradleReleaseAction(FreeStyleProject project) {
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
