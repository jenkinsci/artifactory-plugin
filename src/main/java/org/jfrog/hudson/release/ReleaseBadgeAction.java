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

import hudson.model.BuildBadgeAction;

/**
 * This badge action marks the build as a release build. The release wrapper takes the release and next version string
 * from this badge.
 *
 * @author Yossi Shaul
 */
public class ReleaseBadgeAction implements BuildBadgeAction {
    private transient final String releaseVersion;
    private transient final String nextVersion;

    /**
     * The URL of the VCS tag the plugin created
     */
    private transient String tagUrl;

    public ReleaseBadgeAction(String releaseVersion, String nextVersion) {
        this.releaseVersion = releaseVersion;
        this.nextVersion = nextVersion;
    }

    public String getReleaseVersion() {
        return releaseVersion;
    }

    public String getNextVersion() {
        return nextVersion;
    }

    void setTagUrl(String tagUrl) {
        this.tagUrl = tagUrl;
    }

    /**
     * @return The URL of the VCS tag the plugin created. Null if the tag wasn't created yet.
     */
    String getTagUrl() {
        return tagUrl;
    }

    public String getIconFileName() {
        return "/plugin/artifactory/images/artifactory-release.png";
    }

    public String getDisplayName() {
        return "Artifactory Release";
    }

    public String getUrlName() {
        return null;
    }
}
