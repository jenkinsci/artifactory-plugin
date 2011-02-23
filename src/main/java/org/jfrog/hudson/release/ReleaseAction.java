/*
 * Copyright (C) 2011 JFrog Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jfrog.hudson.release;

import hudson.maven.MavenModuleSet;
import hudson.model.Action;
import hudson.model.Cause;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;

/**
 * This action leads to execution of the release wrapper. It will collect information from the user about the release
 * and will trigger the release build.
 * This action is not saved in the job xml file.
 *
 * @author Yossi Shaul
 */
public class ReleaseAction implements Action {

    private MavenModuleSet project;
    private String releaseVersion;
    private String nextVersion;

    public ReleaseAction(MavenModuleSet project) {
        this.project = project;
    }

    public String getIconFileName() {
        return "/plugin/artifactory/images/artifactory-release.png";
    }

    /**
     * @return The message to display on the left panel for the perform release action.
     */
    public String getDisplayName() {
        return "Release to Artifactory";
    }

    public String getUrlName() {
        return "release";
    }

    public String getReleaseVersion() {
        return releaseVersion;
    }

    public void setReleaseVersion(String releaseVersion) {
        this.releaseVersion = releaseVersion;
    }

    public String getNextVersion() {
        return nextVersion;
    }

    public void setNextVersion(String nextVersion) {
        this.nextVersion = nextVersion;
    }

    public String getCurrentVersion() {
        return project.getRootModule().getVersion();
    }

    public String calculateReleaseVersion() {
        return getCurrentVersion().replace("-SNAPSHOT", "");
    }

    /**
     * Calculates the next snapshot version based on the current release version
     *
     * @param currentVersion A release version
     * @return The next calculated development (snapshot) version
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public String calculateNextVersion() {
        return calculateNextVersion(calculateReleaseVersion());
    }

    /**
     * Calculates the next snapshot version based on the current release version
     *
     * @param currentVersion A release version
     * @return The next calculated development (snapshot) version
     */
    String calculateNextVersion(String currentVersion) {
        String nextVersion;
        int lastDotIndex = currentVersion.lastIndexOf('.');
        try {
            if (lastDotIndex != -1) {
                // probably a major minor version e.g., 2.1.1
                String minorVersionToken = currentVersion.substring(lastDotIndex + 1);
                String nextMinorVersion;
                int lastDashIndex = minorVersionToken.lastIndexOf('-');
                if (lastDashIndex != -1) {
                    // probably a minor-buildNum e.g., 2.1.1-4 (should change to 2.1.1-5)
                    String buildNumber = minorVersionToken.substring(lastDashIndex + 1);
                    int nextBuildNumber = Integer.parseInt(buildNumber) + 1;
                    nextMinorVersion = minorVersionToken.substring(0, lastDashIndex + 1) + nextBuildNumber;
                } else {
                    nextMinorVersion = Integer.parseInt(minorVersionToken) + 1 + "";
                }
                nextVersion = currentVersion.substring(0, lastDotIndex + 1) + nextMinorVersion;
            } else {
                // maybe it's just a major version; try to parse as an int
                int nextMajorVersion = Integer.parseInt(currentVersion) + 1;
                nextVersion = nextMajorVersion + "";
            }
        } catch (NumberFormatException e) {
            nextVersion = "Next.Version";
        }
        return nextVersion + "-SNAPSHOT";
    }

    /**
     * Form submission is calling this method
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public void doSubmit(StaplerRequest req, StaplerResponse resp) throws IOException, ServletException {
        req.bindParameters(this);

        // schedule release build
        if (project.scheduleBuild(0, new Cause.UserCause(), new ReleaseMarkerAction(releaseVersion, nextVersion))) {
            // redirect to the project page
            resp.sendRedirect(project.getAbsoluteUrl());
        }
    }
}
