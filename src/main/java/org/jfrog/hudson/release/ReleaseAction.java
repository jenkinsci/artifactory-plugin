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

import hudson.model.Action;
import hudson.model.AbstractProject;
import hudson.model.Cause;

import java.io.IOException;
import java.util.List;

import javax.servlet.ServletException;

import org.jfrog.hudson.ArtifactoryPlugin;
import org.jfrog.hudson.ArtifactoryServer;
import org.jfrog.hudson.release.scm.AbstractScmCoordinator;
import org.jfrog.hudson.release.scm.svn.SubversionManager;
import org.jfrog.hudson.util.GenericArtifactVersion;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * This action leads to execution of the release wrapper. It will collect information from the user about the release
 * and will trigger the release build. This action is not saved in the job xml file.
 *
 * @author Yossi Shaul
 */
public abstract class ReleaseAction implements Action {

    private final transient AbstractProject project;

    protected VERSIONING versioning;

    /**
     * The next release version to change the model to if using one global version.
     */
    protected String releaseVersion;
    /**
     * Next (development) version to change the model to if using one global version.
     */
    protected String nextVersion;

    Boolean pro;


    boolean createVcsTag;
    String tagUrl;
    String tagComment;
    String nextDevelCommitComment;
    String stagingRepositoryKey;
    String stagingComment;

    boolean createReleaseBranch;
    String releaseBranch;

    public enum VERSIONING {
        GLOBAL, PER_MODULE, NONE
    }

    public ReleaseAction(AbstractProject project) {
        this.project = project;
    }

    public String getIconFileName() {
        if (project.hasPermission(ArtifactoryPlugin.RELEASE)) {
            return "/plugin/artifactory/images/artifactory-release.png";
        }

        // return null to hide the action (doSubmit will also perform permission check if someone tries direct link)
        return null;
    }

    /**
     * @return The message to display on the left panel for the perform release action.
     */
    public String getDisplayName() {
        return "Artifactory Release Staging";
    }

    public String getUrlName() {
        return "release";
    }

    public VERSIONING getVersioning() {
        return versioning;
    }

    public String getReleaseVersion() {
        return releaseVersion;
    }

    public String getNextVersion() {
        return nextVersion;
    }

    public boolean isCreateVcsTag() {
        return createVcsTag;
    }

    public String getTagUrl() {
        return tagUrl;
    }

    public String getTagComment() {
        return tagComment;
    }

    public String getNextDevelCommitComment() {
        return nextDevelCommitComment;
    }

    public boolean isCreateReleaseBranch() {
        return createReleaseBranch;
    }

    public String getReleaseBranch() {
        return releaseBranch;
    }

    public String getStagingComment() {
        return stagingComment;
    }

    public String getStagingRepositoryKey() {
        return stagingRepositoryKey;
    }

    public String latestVersioningSelection() {
        return VERSIONING.GLOBAL.name();
    }

    public boolean isArtifactoryPro() {
        return getArtifactoryServer().isArtifactoryPro();
    }

    protected String getBaseTagUrlAccordingToScm(String baseTagUrl) {
        if (AbstractScmCoordinator.isSvn(project) && !baseTagUrl.endsWith("/")) {
            return baseTagUrl + "/";
        }
        return baseTagUrl;
    }

    public abstract String getDefaultTagUrl();

    public abstract String getDefaultReleaseBranch();

    public String getDefaultReleaseComment() {
        return SubversionManager.COMMENT_PREFIX + "Release version " + calculateReleaseVersion();
    }

    public String getDefaultNextDevelCommitMessage() {
        return SubversionManager.COMMENT_PREFIX + "Next development version";
    }

    public boolean isGit() {
        return AbstractScmCoordinator.isGitScm(project);
    }

    /**
     * @return The release repository configured in Artifactory publisher.
     */
    public abstract String getDefaultStagingRepository();

    public String calculateReleaseVersion() {
        return calculateReleaseVersion(getCurrentVersion());
    }

    public abstract String getCurrentVersion();

    public String calculateReleaseVersion(String fromVersion) {
    	String releaseVersion;
    	
    	try {
    		releaseVersion = new GenericArtifactVersion(fromVersion).upgradeLeastSignificantNumber().setBuildSpecifier("SNAPSHOT").toString(); 
    	}
    	catch (IllegalArgumentException e) {
    		// if 'fromVersion' cannot be parsed, use it as the default release version
    		releaseVersion = fromVersion;
    	}
    	
    	return releaseVersion;
    }

    /**
     * Calculates the next snapshot version based on the current release version
     *
     * @return The next calculated development (snapshot) version
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public String calculateNextVersion() {
        return calculateNextVersion(calculateReleaseVersion());
    }

    /**
     * Calculates the next snapshot version based on the current release version
     *
     * @param fromVersion The version to bump to next development version
     * @return The next calculated development (snapshot) version
     */
    public String calculateNextVersion(String fromVersion) {
    	String nextVersion;
    	
    	try {
    		nextVersion = new GenericArtifactVersion(fromVersion).upgradeLeastSignificantNumber().setBuildSpecifier("SNAPSHOT").toString(); 
    	}
    	catch (IllegalArgumentException e) {
    		// if 'fromVersion' cannot be parsed, use it as the default next dev version.
    		nextVersion = fromVersion;
    	}
    	
    	return nextVersion;
   	}

    /**
     * @return List of target repositories for deployment (release repositories first). Called from the UI.
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public abstract List<String> getRepositoryKeys();

    public abstract ArtifactoryServer getArtifactoryServer();

    // prefer the release repository defined in artifactory publisher
    public abstract String lastStagingRepository();

    /**
     * Form submission is calling this method
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public void doSubmit(StaplerRequest req, StaplerResponse resp) throws IOException, ServletException {
        // enforce release permissions
        project.checkPermission(ArtifactoryPlugin.RELEASE);

        req.bindParameters(this);

        String versioningStr = req.getParameter("versioning");
        versioning = VERSIONING.valueOf(versioningStr);
        switch (versioning) {
            case GLOBAL:
                doGlobalVersioning(req);
                break;
            case PER_MODULE:
                doPerModuleVersioning(req);
        }
        createVcsTag = req.getParameter("createVcsTag") != null;
        if (createVcsTag) {
            tagUrl = req.getParameter("tagUrl");
            tagComment = req.getParameter("tagComment");
        }
        nextDevelCommitComment = req.getParameter("nextDevelCommitComment");
        createReleaseBranch = req.getParameter("createReleaseBranch") != null;
        if (createReleaseBranch) {
            releaseBranch = req.getParameter("releaseBranch");
        }

        stagingRepositoryKey = req.getParameter("repositoryKey");
        stagingComment = req.getParameter("stagingComment");

        // schedule release build
        if (project.scheduleBuild(0, new Cause.UserIdCause(), this)) {
            // redirect to the project page
            resp.sendRedirect(project.getAbsoluteUrl());
        }
    }

    /**
     * Execute the {@link VERSIONING#PER_MODULE} strategy of the versioning mechanism, which assigns each module its own
     * version for release and for the next development version
     *
     * @param req The request that is coming from the form when staging.
     */
    protected abstract void doPerModuleVersioning(StaplerRequest req);

    /**
     * Execute the {@link VERSIONING#GLOBAL} strategy of the versioning mechanism, which assigns all modules the same
     * version for the release and for the next development version.
     *
     * @param req The request that is coming from the form when staging.
     */
    protected void doGlobalVersioning(StaplerRequest req) {
        releaseVersion = req.getParameter("releaseVersion");
        nextVersion = req.getParameter("nextVersion");
    }

    public abstract String getReleaseVersionFor(Object moduleName);

    public abstract String getNextVersionFor(Object moduleName);
}
