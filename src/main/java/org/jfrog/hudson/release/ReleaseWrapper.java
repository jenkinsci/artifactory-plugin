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
import hudson.model.Descriptor;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.scm.SubversionSCM;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.tasks.Publisher;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import org.apache.commons.lang.StringUtils;
import org.jfrog.hudson.ArtifactoryRedeployPublisher;
import org.jfrog.hudson.ServerDetails;
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

        final ReleaseAction releaseAction = build.getAction(ReleaseAction.class);
        if (releaseAction == null) {
            // this is a normal non release build, continue with normal environment
            return new Environment() {
            };
        }

        log(listener, "Release build triggered");

        final MavenModuleSetBuild mavenBuild = (MavenModuleSetBuild) build;
        if (!releaseAction.versioning.equals(ReleaseAction.VERSIONING.NONE)) {
            // change to release version
            String vcsUrl = releaseAction.createVcsTag ? getBaseTagUrl() : null;
            changeVersions(mavenBuild, releaseAction, true, vcsUrl);
        }

        final MavenModuleSet mavenModuleSet = mavenBuild.getProject();
        final String originalGoals = mavenModuleSet.getGoals();
        if (!StringUtils.isBlank(alternativeGoals)) {
            debuggingLogger.fine("Using alternative goals and settings: " + alternativeGoals);
            mavenModuleSet.setGoals(alternativeGoals);
        }

        // change the target repository in the redeploy publisher if configured differently
        DescribableList<Publisher, Descriptor<Publisher>> publishers = mavenModuleSet.getPublishers();
        String publisherRepositoryKey = null;
        for (Publisher publisher : publishers) {
            if (publisher instanceof ArtifactoryRedeployPublisher) {
                publisherRepositoryKey = ((ArtifactoryRedeployPublisher) publisher).getRepositoryKey();
                if (!releaseAction.stagingRepositoryKey.equals(publisherRepositoryKey)) {
                    ServerDetails details = ((ArtifactoryRedeployPublisher) publisher).getDetails();
                    if (details != null) {
                        details.repositoryKey = releaseAction.releaseVersion;
                    }
                }
                break;
            }
        }
        final String publisherRepositoryKeyToRestore =
                releaseAction.stagingRepositoryKey.equals(publisherRepositoryKey) ? null : publisherRepositoryKey;

        return new Environment() {
            @Override
            public boolean tearDown(AbstractBuild build, BuildListener listener)
                    throws IOException, InterruptedException {
                // if we used alternative goals set back the original
                if (!StringUtils.isBlank(alternativeGoals)) {
                    mavenModuleSet.setGoals(originalGoals);
                }

                // if we changed the publisher target repository set it back to the original
                if (!StringUtils.isBlank(publisherRepositoryKeyToRestore)) {
                    DescribableList<Publisher, Descriptor<Publisher>> publishers = mavenModuleSet.getPublishers();
                    for (Publisher publisher : publishers) {
                        if (publisher instanceof ArtifactoryRedeployPublisher) {
                            ServerDetails details = ((ArtifactoryRedeployPublisher) publisher).getDetails();
                            if (details != null) {
                                details.repositoryKey = publisherRepositoryKeyToRestore;
                            }
                            break;
                        }
                    }
                }

                SubversionManager svn = new SubversionManager(build, listener);
                if (build.getResult().isWorseThan(Result.SUCCESS)) {
                    // revert will happen by the listener
                    return true;
                }

                if (releaseAction.createVcsTag) {
                    // create subversion tag
                    try {
                        svn.createTag(releaseAction.tagUrl, releaseAction.tagComment);
                        releaseAction.tagCreated = true;
                    } catch (IOException e) {
                        // revert working copy and re-throw the original exception
                        svn.safeRevertWorkingCopy();
                        throw e;
                    }
                }

                if (!releaseAction.versioning.equals(ReleaseAction.VERSIONING.NONE)) {
                    // change poms versions to next development version
                    String scmUrl = releaseAction.createVcsTag ? svn.getLocation().remote : null;
                    changeVersions(mavenBuild, releaseAction, false, scmUrl);
                }

                svn.commitWorkingCopy("Committing next development version");

                return true;
            }
        };
    }

    private void changeVersions(MavenModuleSetBuild mavenBuild, ReleaseAction release, boolean releaseVersion,
            String scmUrl)
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
            String newVersion = releaseVersion ? release.getReleaseVersionFor(mavenModule.getModuleName()) :
                    release.getNextVersionFor(mavenModule.getModuleName());
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
            return "Enable release to Artifactory";
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
            ReleaseAction releaseAction = mavenBuild.getAction(ReleaseAction.class);
            if (releaseAction == null) {
                return;
            }

            // remove the release action from the build. the stage action is the point of interaction for successful builds
            mavenBuild.getActions().remove(releaseAction);

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
            if (releaseAction.tagCreated) {
                svn.safeRevertTag(releaseAction.tagUrl, "Reverting vcs tag: " + releaseAction.tagUrl);
            }
        }
    }
}
