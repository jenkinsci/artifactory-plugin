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

import com.google.common.collect.Maps;
import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.maven.MavenModule;
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.maven.ModuleName;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.scm.SubversionSCM;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.FormValidation;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.logging.Logger;

/**
 * A release wrapper for maven projects. Allows performing release steps on maven
 *
 * @author Yossi Shaul
 */
public class ReleaseWrapper extends BuildWrapper {
    private static Logger debuggingLogger = Logger.getLogger(ReleaseWrapper.class.getName());

    private String baseTagUrl;
    private String alternativeGoals;

    @DataBoundConstructor
    public ReleaseWrapper(String baseTagUrl, String alternativeGoals) {
        this.baseTagUrl = baseTagUrl;
        this.alternativeGoals = alternativeGoals;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public String getBaseTagUrl() {
        return baseTagUrl;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void setBaseTagUrl(String baseTagUrl) {
        this.baseTagUrl = baseTagUrl;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public String getAlternativeGoals() {
        return alternativeGoals;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void setAlternativeGoals(String alternativeGoals) {
        this.alternativeGoals = alternativeGoals;
    }

    @Override
    public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener)
            throws IOException, InterruptedException {

        final ReleaseMarkerAction releaseBadge = build.getAction(ReleaseMarkerAction.class);
        if (releaseBadge == null) {
            // this is a normal non release build, continue with normal environment
            return new Environment() {
            };
        }

        if (StringUtils.isBlank(baseTagUrl)) {
            throw new AbortException("Base tags URL not configured. Please check your configuration.");
        }

        if (StringUtils.isBlank(releaseBadge.getReleaseVersion()) || StringUtils.isBlank(
                releaseBadge.getNextVersion())) {
            throw new AbortException("Release and development versions are mandatory");
        }

        // change the version
        final MavenModuleSetBuild mavenBuild = (MavenModuleSetBuild) build;
        final MavenModuleSet mavenModuleSet = mavenBuild.getProject();
        final MavenModule rootModule = mavenModuleSet.getRootModule();
        log(listener, String.format("Changing POMs from version %s to %s%n",
                rootModule.getVersion(), releaseBadge.getReleaseVersion()));
        final String tagUrl = buildTagUrl(releaseBadge, rootModule);
        changeVersions(mavenBuild, releaseBadge.getReleaseVersion(), tagUrl);

        final String originalGoals = mavenModuleSet.getGoals();
        if (!StringUtils.isBlank(alternativeGoals)) {
            debuggingLogger.fine("Using alternative goals and settings: " + alternativeGoals);
            mavenModuleSet.setGoals(alternativeGoals);
        }

        return new Environment() {
            @Override
            public boolean tearDown(AbstractBuild build, BuildListener listener)
                    throws IOException, InterruptedException {
                // if we used alternative goals set back the original
                if (!StringUtils.isBlank(alternativeGoals)) {
                    mavenModuleSet.setGoals(originalGoals);
                }

                SubversionManager svn = new SubversionManager(build, listener);
                if (build.getResult().isWorseThan(Result.SUCCESS)) {
                    // revert will happen by the listener
                    return true;
                }

                // create subversion tag
                try {
                    svn.createTag(tagUrl, "Creating release tag for version " + releaseBadge.getReleaseVersion());
                    releaseBadge.setTagUrl(tagUrl);
                } catch (IOException e) {
                    // revert working copy and re-throw the original exception
                    svn.safeRevertWorkingCopy();
                    throw e;
                }

                // change poms versions to next development version
                log(listener, "Changing POMs to next development version: " + releaseBadge.getNextVersion());
                String scmUrl = svn.getLocation().remote;
                changeVersions(mavenBuild, releaseBadge.getNextVersion(), scmUrl);

                svn.commitWorkingCopy("Committing next development version: " + releaseBadge.getNextVersion());

                return true;
            }
        };
    }

    private String buildTagUrl(ReleaseMarkerAction releaseBadge, MavenModule rootModule) {
        StringBuilder sb = new StringBuilder(baseTagUrl);
        if (!baseTagUrl.endsWith("/")) {
            sb.append("/");
        }
        sb.append(rootModule.getModuleName().artifactId).append("-").append(releaseBadge.getReleaseVersion());
        return sb.toString();
    }

    private void changeVersions(MavenModuleSetBuild mavenBuild, String newVersion, String scmUrl)
            throws IOException, InterruptedException {
        FilePath moduleRoot = mavenBuild.getModuleRoot();
        // get the active modules only
        Collection<MavenModule> modules = mavenBuild.getProject().getDisabledModules(false);

        Map<ModuleName, MavenModule> modulesByName = Maps.newHashMap();
        for (MavenModule module : modules) {
            modulesByName.put(module.getModuleName(), module);
        }

        for (MavenModule mavenModule : modules) {
            String relativePath = mavenModule.getRelativePath();
            String pomRelativePath = StringUtils.isBlank(relativePath) ? "pom.xml" : relativePath + "/pom.xml";
            FilePath pomPath = new FilePath(moduleRoot, pomRelativePath);
            debuggingLogger.fine("Changing version of pom: " + pomPath);
            pomPath.act(new PomTransformer(modulesByName, newVersion, scmUrl));
        }
    }

    private void log(BuildListener listener, String message) {
        listener.getLogger().println("[RELEASE] " + message);
    }

    @Override
    public Collection<? extends Action> getProjectActions(AbstractProject job) {
        return Arrays.asList(new ReleaseAction((MavenModuleSet) job));
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
            return (item instanceof MavenModuleSet) && (item.getScm() instanceof SubversionSCM);
        }

        /**
         * @return The message to be displayed next to the checkbox in the job configuration.
         */
        @Override
        public String getDisplayName() {
            return "Configure Artifactory release";
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
            if (!(run instanceof MavenModuleSetBuild)) {
                return;
            }

            MavenModuleSetBuild mavenBuild = (MavenModuleSetBuild) run;
            ReleaseMarkerAction releaseBadge = mavenBuild.getAction(ReleaseMarkerAction.class);
            if (releaseBadge == null) {
                return;
            }

            Result result = mavenBuild.getResult();
            if (result.isBetterOrEqualTo(Result.SUCCESS)) {
                // add a stage action
                mavenBuild.addAction(new StageBuildAction(mavenBuild));
                return;
            }

            // build has failed, make sure to delete the tag and revert the working copy
            //run.getActions().remove(releaseBadge);
            SubversionManager svn = new SubversionManager(mavenBuild, listener);
            svn.safeRevertWorkingCopy();
            if (releaseBadge.getTagUrl() != null) {
                svn.safeRevertTag(releaseBadge.getTagUrl(),
                        "Reverting release tag for version " + releaseBadge.getReleaseVersion());
            }
        }
    }

}
