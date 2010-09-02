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

import com.google.common.collect.Lists;
import hudson.maven.MavenBuild;
import hudson.maven.reporters.MavenArtifactRecord;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.CauseAction;

import java.util.Collections;
import java.util.List;

/**
 * @author Yossi Shaul
 */
public abstract class ActionableHelper {

    public static MavenArtifactRecord getLatestMavenArtifactRecord(MavenBuild mavenBuild) {
        return getLatestAction(mavenBuild, MavenArtifactRecord.class);
    }

    public static <T extends Action> T getLatestAction(AbstractBuild mavenBuild, Class<T> actionClass) {
        // one module may produce multiple action entries of the same type, the last one contains all the info we need
        // (previous ones might only contain partial information, eg, only main artifact)
        List<T> records = mavenBuild.getActions(actionClass);
        if (records == null || records.isEmpty()) {
            return null;
        } else {
            return records.get(records.size() - 1);
        }
    }

    public static Cause.UpstreamCause getUpstreamCause(AbstractBuild build) {
        CauseAction action = ActionableHelper.getLatestAction(build, CauseAction.class);
        if (action != null) {
            for (Cause cause : action.getCauses()) {
                if (cause instanceof Cause.UpstreamCause) {
                    return (Cause.UpstreamCause) cause;
                }
            }
        }
        return null;
    }

    public static String getHudsonPrincipal(AbstractBuild build) {
        CauseAction action = getLatestAction(build, CauseAction.class);
        String principal = "";
        if (action != null) {
            for (Cause cause : action.getCauses()) {
                if (cause instanceof Cause.UserCause) {
                    principal = (((Cause.UserCause) cause).getUserName());
                }
            }
        }
        return principal;
    }

    /**
     * Return list with {@link ArtifactoryProjectAction} if not already exists in project actions.
     *
     * @param artifactoryRootUrl The root URL of Artifactory server
     * @param project            The hudson project
     * @return Empty list or list with one {@link ArtifactoryProjectAction}
     */
    public static List<ArtifactoryProjectAction> getArtifactoryProjectAction(
            String artifactoryRootUrl, AbstractProject project) {
        if (artifactoryRootUrl == null) {
            return Collections.emptyList();
        }
        if (project.getAction(ArtifactoryProjectAction.class) != null) {
            // don't add if already exist (if multiple Artifactory builders are configured in free style)
            return Collections.emptyList();
        }
        return Lists.newArrayList(new ArtifactoryProjectAction(artifactoryRootUrl, project));
    }
}
