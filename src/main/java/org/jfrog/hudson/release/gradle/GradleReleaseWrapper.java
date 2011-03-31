/*
 * Copyright (C) 2011 JFrog Ltd.
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

package org.jfrog.hudson.release.gradle;

import com.google.common.collect.Maps;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.BuildableItemWithBuildWrappers;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.FormValidation;
import org.apache.commons.lang.StringUtils;
import org.jfrog.hudson.action.ActionableHelper;
import org.jfrog.hudson.release.GradlePromoteBuildAction;
import org.jfrog.hudson.release.scm.AbstractScmCoordinator;
import org.jfrog.hudson.release.scm.ScmCoordinator;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A release wrapper for Gradle projects. Allows performing release steps on Gradle.
 *
 * @author Tomer Cohen
 */
public class GradleReleaseWrapper extends BuildWrapper {
    private final static Logger debuggingLogger = Logger.getLogger(GradleReleaseWrapper.class.getName());

    private String tagPrefix;
    private String releaseBranchPrefix;
    private String alternativeGoals;
    private String versionPropKeys;
    private String additionalPropKeys;

    private transient ScmCoordinator scmCoordinator;

    @DataBoundConstructor
    public GradleReleaseWrapper(String releaseBranchPrefix, String tagPrefix, String alternativeGoals,
            String versionPropKeys, String additionalPropKeys) {
        this.releaseBranchPrefix = releaseBranchPrefix;
        this.tagPrefix = tagPrefix;
        this.alternativeGoals = alternativeGoals;
        this.versionPropKeys = versionPropKeys;
        this.additionalPropKeys = additionalPropKeys;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public String getTagPrefix() {
        return tagPrefix;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void setTagPrefix(String tagPrefix) {
        this.tagPrefix = tagPrefix;
    }

    public String getVersionPropKeys() {
        return versionPropKeys;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void setVersionPropKeys(String versionPropKeys) {
        this.versionPropKeys = versionPropKeys;
    }

    public String getAdditionalPropKeys() {
        return additionalPropKeys;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void setAdditionalPropKeys(String additionalPropKeys) {
        this.additionalPropKeys = additionalPropKeys;
    }

    public String getReleaseBranchPrefix() {
        return releaseBranchPrefix;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void setReleaseBranchPrefix(String releaseBranchPrefix) {
        this.releaseBranchPrefix = releaseBranchPrefix;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public String getAlternativeGoals() {
        return alternativeGoals;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void setAlternativeGoals(String alternativeGoals) {
        this.alternativeGoals = alternativeGoals;
    }

    public String[] getVersionPropsKeysList() {
        return stringToArray(getVersionPropKeys());
    }

    public String[] getAdditionalPropsKeysList() {
        return stringToArray(getAdditionalPropKeys());
    }

    private String[] stringToArray(String commaSeparatedString) {
        commaSeparatedString = StringUtils.trimToEmpty(commaSeparatedString);
        return StringUtils.split(commaSeparatedString, ", ");
    }

    @Override
    public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener)
            throws IOException, InterruptedException {
        final GradleReleaseAction releaseAction = build.getAction(GradleReleaseAction.class);
        if (releaseAction == null) {
            // this is a normal non release build, continue with normal environment
            return new Environment() {
            };
        }
        log(listener, "Release build triggered");
        scmCoordinator = AbstractScmCoordinator.createScmCoordinator(build, listener, releaseAction);
        scmCoordinator.prepare();
        // TODO: replace the versioning mode with something else
        if (!releaseAction.getVersioning().equals(GradleReleaseAction.VERSIONING.NONE)) {
            scmCoordinator.beforeReleaseVersionChange();
            // change to release properties values
            boolean modified = changeProperties(build, releaseAction, true, listener);
            scmCoordinator.afterReleaseVersionChange(modified);
        }
        return new Environment() {
            @Override
            public boolean tearDown(AbstractBuild build, BuildListener listener)
                    throws IOException, InterruptedException {
                // if we used alternative goals set back the original
                if (build.getResult().isWorseThan(Result.SUCCESS)) {
                    // revert will happen by the listener
                    return true;
                }

                try {
                    scmCoordinator.afterSuccessfulReleaseVersionBuild();
                    if (!releaseAction.getVersioning().equals(GradleReleaseAction.VERSIONING.NONE)) {
                        scmCoordinator.beforeDevelopmentVersionChange();
                        boolean modified = changeProperties(build, releaseAction, false, listener);
                        scmCoordinator.afterDevelopmentVersionChange(modified);
                    }
                } catch (Exception e) {
                    listener.getLogger().println("Failure in post build SCM action: " + e.getMessage());
                    debuggingLogger.log(Level.FINE, "Failure in post build SCM action: ", e);
                    return false;
                }
                return true;
            }
        };
    }

    private boolean changeProperties(AbstractBuild build, GradleReleaseAction release, boolean releaseVersion,
            BuildListener listener) throws IOException, InterruptedException {
        FilePath root = build.getModuleRoot();
        debuggingLogger.fine("Root directory is: " + root.getRemote());
        String[] modules = release.getVersionProperties();
        Map<String, String> modulesByName = Maps.newHashMap();
        for (String module : modules) {
            String version = releaseVersion ? release.getReleaseVersionFor(module) :
                    release.getNextVersionFor(module);
            modulesByName.put(module, version);
        }

        String[] additionalModuleProperties = release.getAdditionalProperties();
        for (String property : additionalModuleProperties) {
            String version = releaseVersion ? release.getReleaseVersionFor(property) :
                    release.getNextVersionFor(property);
            modulesByName.put(property, version);
        }
        debuggingLogger.fine("Changing version of gradle properties");
        FilePath gradlePropertiesFilePath = new FilePath(root, "gradle.properties");
        String next = releaseVersion ? "release" : "development";
        log(listener,
                "Changing gradle.properties at " + gradlePropertiesFilePath.getRemote() + "to " + next + " version");
        return gradlePropertiesFilePath.act(new PropertiesTransformer(modulesByName));
    }

    private void log(BuildListener listener, String message) {
        listener.getLogger().println("[RELEASE] " + message);
    }

    @Override
    public Collection<? extends Action> getProjectActions(AbstractProject job) {
        return Arrays.asList(new GradleReleaseAction((FreeStyleProject) job));
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static final class DescriptorImpl extends BuildWrapperDescriptor {
        /**
         * This wrapper applied to maven projects with subversion only.
         *
         * @param item The current project
         * @return True for maven projects with subversion as the scm
         */
        @Override
        public boolean isApplicable(AbstractProject<?, ?> item) {
            return (item instanceof FreeStyleProject)/* && (item.getScm() instanceof SubversionSCM)*/;
        }

        /**
         * @return The message to be displayed next to the checkbox in the job configuration.
         */
        @Override
        public String getDisplayName() {
            return "Enable Artifactory Gradle release management";
        }

        /**
         * @param baseTagUrl The subversion tags url
         * @return Error message if tags url is not set
         */
        @SuppressWarnings({"UnusedDeclaration"})
        public FormValidation doCheckBaseTagUrl(@QueryParameter String baseTagUrl) {
            String trimmedUrl = hudson.Util.fixEmptyAndTrim(baseTagUrl);
            if (trimmedUrl == null) {
                return FormValidation.error("Subversion base tags URL is mandatory");
            }
            return FormValidation.ok();
        }
    }

    /**
     * This run listener handles the job completed event to cleanup svn tags and working copy in case of build failure.
     */
    @Extension
    public static final class ReleaseRunListener extends RunListener<AbstractBuild> {
        /**
         * Completed event is sent after the build and publishers execution. The build result in this stage is final and
         * cannot be modified. So this is a good place to revert working copy and tag if the build failed.
         */
        @Override
        public void onCompleted(AbstractBuild run, TaskListener listener) {
            if (!(run instanceof FreeStyleBuild)) {
                return;
            }

            GradleReleaseAction releaseAction = run.getAction(GradleReleaseAction.class);
            if (releaseAction == null) {
                return;
            }
            releaseAction.reset();

            Result result = run.getResult();
            if (result.isBetterOrEqualTo(Result.SUCCESS)) {
                // add a stage action
                run.addAction(new GradlePromoteBuildAction(run));
            }

            // signal completion to the scm coordinator
            GradleReleaseWrapper wrapper = ActionableHelper.getBuildWrapper(
                    (BuildableItemWithBuildWrappers) run.getProject(), GradleReleaseWrapper.class);
            try {
                wrapper.scmCoordinator.buildCompleted();
            } catch (Exception e) {
                run.setResult(Result.FAILURE);
                listener.error("[RELEASE] Failed on build completion");
                e.printStackTrace(listener.getLogger());
            }

            // remove the release action from the build. the stage action is the point of interaction for successful builds
            run.getActions().remove(releaseAction);
        }
    }
}

