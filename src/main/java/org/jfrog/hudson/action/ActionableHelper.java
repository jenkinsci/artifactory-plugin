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
import hudson.model.*;
import hudson.tasks.BuildWrapper;
import hudson.tasks.Builder;
import hudson.tasks.Publisher;
import hudson.util.DescribableList;
import org.apache.commons.lang.StringUtils;

import java.util.Collections;
import java.util.List;

/**
 * @author Yossi Shaul
 */
public abstract class ActionableHelper {

    public static MavenArtifactRecord getLatestMavenArtifactRecord(MavenBuild mavenBuild) {
        return getLatestAction(mavenBuild, MavenArtifactRecord.class);
    }

    /**
     * Returns the latest action of the type. One module may produce multiple action entries of the same type, in some
     * cases the last one contains all the info we need (previous ones might only contain partial information, eg, only
     * main artifact)
     *
     * @param build       The build
     * @param actionClass The type of the action
     * @return Latest action of the given type or null if not found
     */
    public static <T extends Action> T getLatestAction(AbstractBuild build, Class<T> actionClass) {
        List<T> records = build.getActions(actionClass);
        if (records == null || records.isEmpty()) {
            return null;
        } else {
            return records.get(records.size() - 1);
        }
    }

    /**
     * Get the root build which triggered the current build.
     *
     * @param currentBuild The current build.
     * @return The root build.
     */
    public static AbstractBuild<?, ?> getRootBuild(AbstractBuild<?, ?> currentBuild) {
        AbstractBuild<?, ?> rootBuild = null;
        while (getUpstreamCause(currentBuild) != null) {
            Cause.UpstreamCause cause = getUpstreamCause(currentBuild);
            AbstractProject<?, ?> project = getProject(cause.getUpstreamProject());
            if (project == null) {
                throw new RuntimeException("No project found answering for the name: " + cause.getUpstreamProject());
            }
            rootBuild = getBuildByNumber(project, cause.getUpstreamBuild());
            if (rootBuild == null) {
                throw new RuntimeException(
                        "No build with name: " + project.getName() + " and number: " + cause.getUpstreamBuild());
            }
            currentBuild = rootBuild;
        }
        if (rootBuild == null) {
            return currentBuild;
        }
        return rootBuild;
    }

    /**
     * Get a build according to the build's name (which is actually the project's name) and the build number.
     *
     * @param project     The project from which to get the build's name.
     * @param buildNumber The build number.
     * @return The build which answers for the name and number.
     */
    private static AbstractBuild<?, ?> getBuildByNumber(AbstractProject<?, ?> project, int buildNumber) {
        return project.getBuildByNumber(buildNumber);
    }

    /**
     * Get a project according to its name.
     *
     * @param name The name of the project.
     * @return The project which answers the name.
     */
    private static AbstractProject<?, ?> getProject(String name) {
        TopLevelItem item = Hudson.getInstance().getItem(name);
        if (item != null && item instanceof AbstractProject) {
            return (AbstractProject<?, ?>) item;
        }
        return null;
    }

    /**
     * @return The project publisher of the given type. Null if not found.
     */
    public static <T extends Publisher> T getPublisher(AbstractProject<?, ?> project, Class<T> type) {
        DescribableList<Publisher, Descriptor<Publisher>> publishersList = project.getPublishersList();
        for (Publisher publisher : publishersList) {
            if (type.isInstance(publisher)) {
                return type.cast(publisher);
            }
        }
        return null;
    }

    /**
     * @return The wrapped item (eg, project) wrapper of the given type. Null if not found.
     */
    public static <T extends BuildWrapper> T getBuildWrapper(BuildableItemWithBuildWrappers wrapped,
            Class<T> type) {
        DescribableList<BuildWrapper, Descriptor<BuildWrapper>> wrappers = wrapped.getBuildWrappersList();
        for (BuildWrapper wrapper : wrappers) {
            if (type.isInstance(wrapper)) {
                return type.cast(wrapper);
            }
        }
        return null;
    }

    /**
     * Get a list of {@link Builder}s that are related to the project.
     *
     * @param project The project from which to get the builder.
     * @param type    The type of the builder (the actual class)
     * @param <T>     The type that the class represents
     * @return A list of builders that answer the class definition that are attached to the project.
     */
    public static <T extends Builder> List<T> getBuilder(Project<?, ?> project, Class<T> type) {
        List<T> result = Lists.newArrayList();
        DescribableList<Builder, Descriptor<Builder>> builders = project.getBuildersList();
        for (Builder builder : builders) {
            if (type.isInstance(builder)) {
                result.add(type.cast(builder));
            }
        }
        return result;
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

    public static Cause.UserCause getUserCause(AbstractBuild build) {
        CauseAction action = ActionableHelper.getLatestAction(build, CauseAction.class);
        if (action != null) {
            for (Cause cause : action.getCauses()) {
                if (cause instanceof Cause.UserCause) {
                    return (Cause.UserCause) cause;
                }
            }
        }
        return null;
    }

    public static String getBuildUrl(AbstractBuild build) {
        String root = Hudson.getInstance().getRootUrl();
        if (StringUtils.isBlank(root)) {
            return "";
        }
        return root + build.getUrl();
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
