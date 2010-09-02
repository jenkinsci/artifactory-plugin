/*
 * Copyright (C) 2010 JFrog Ltd.
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

package org.jfrog.hudson.action;

import hudson.model.AbstractProject;
import hudson.model.ProminentProjectAction;

/**
 * {@link hudson.model.ProminentProjectAction} that links to the latest build in Artifactory.
 *
 * @author Yossi Shaul
 */
public class ArtifactoryProjectAction implements ProminentProjectAction {
    private final String url;

    public ArtifactoryProjectAction(String artifactoryRootUrl, AbstractProject<?, ?> project) {
        if (artifactoryRootUrl != null) {
            url = generateUrl(artifactoryRootUrl, project);
        } else {
            url = "";
        }
    }

    public String getIconFileName() {
        return "/plugin/artifactory/images/artifactory-icon.png";
    }

    public String getDisplayName() {
        return "Artifactory";
    }

    public String getUrlName() {
        return url;
    }

    private String generateUrl(String artifactoryRootUrl, AbstractProject<?, ?> project) {
        return artifactoryRootUrl + "/webapp/builds/" + project.getDisplayName();
    }
}