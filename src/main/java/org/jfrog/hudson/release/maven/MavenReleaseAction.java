package org.jfrog.hudson.release.maven;

import hudson.maven.MavenModuleSet;
import org.jfrog.hudson.ArtifactoryPlugin;

public class MavenReleaseAction extends BaseMavenReleaseAction {

    public MavenReleaseAction(MavenModuleSet project) {
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