/*
 * Copyright (C) 2011 JFrog Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jfrog.hudson.release;

import com.google.common.collect.Lists;
import hudson.model.AbstractBuild;
import hudson.model.BuildBadgeAction;
import hudson.model.Cause;
import hudson.model.TaskAction;
import hudson.model.TaskListener;
import hudson.model.TaskThread;
import hudson.security.ACL;
import hudson.security.Permission;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.jfrog.build.api.builder.PromotionBuilder;
import org.jfrog.build.client.ArtifactoryBuildInfoClient;
import org.jfrog.hudson.ArtifactoryRedeployPublisher;
import org.jfrog.hudson.ArtifactoryServer;
import org.jfrog.hudson.action.ActionableHelper;
import org.jfrog.hudson.release.maven.MavenReleaseWrapper;
import org.jfrog.hudson.util.CredentialResolver;
import org.jfrog.hudson.util.Credentials;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

/**
 * This badge action marks the build as a release build. The release wrapper takes the release and next version string
 * from this badge.
 *
 * @author Yossi Shaul
 */
public class StageBuildAction extends TaskAction implements BuildBadgeAction {
    private final AbstractBuild build;

    private String targetStatus;
    private String repositoryKey;
    private String comment;
    private boolean useCopy;
    private boolean includeDependencies;

    public StageBuildAction(AbstractBuild build) {
        this.build = build;
    }

    public String getIconFileName() {
        return "/plugin/artifactory/images/artifactory-release.png";
    }

    public String getDisplayName() {
        return "Artifactory Release Promotion";
    }

    public String getUrlName() {
        if (hasStagingPermission()) {
            return "stage";
        }
        // return null to hide this action
        return null;
    }

    public boolean hasStagingPermission() {
        return getACL().hasPermission(getPermission());
    }

    @Override
    protected Permission getPermission() {
        return MavenReleaseWrapper.STAGE;
    }

    @Override
    protected ACL getACL() {
        return build.getACL();
    }

    public AbstractBuild getBuild() {
        return build;
    }

    public void setTargetStatus(String targetStatus) {
        this.targetStatus = targetStatus;
    }

    public void setRepositoryKey(String repositoryKey) {
        this.repositoryKey = repositoryKey;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public void setUseCopy(boolean useCopy) {
        this.useCopy = useCopy;
    }

    public void setIncludeDependencies(boolean includeDependencies) {
        this.includeDependencies = includeDependencies;
    }

    /**
     * @return List of target repositories for deployment (release repositories first). Called from the UI.
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public List<String> getRepositoryKeys() {
        ArtifactoryRedeployPublisher artifactoryPublisher = ActionableHelper.getPublisher(
                build.getProject(), ArtifactoryRedeployPublisher.class);
        if (artifactoryPublisher != null) {
            List<String> repos = artifactoryPublisher.getArtifactoryServer().getReleaseRepositoryKeysFirst();
            repos.add(0, "");  // option not to move
            return repos;
        } else {
            return Collections.emptyList();
        }
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public List<String> getTargetStatuses() {
        return Lists.newArrayList(/*"Staged", */"Released", "Rolled-back");
    }

    /**
     * @return The repository selected by the latest promotion (to be selected by default).
     */
    public String lastPromotionRepository() {
        // TODO: implement
        return null;
    }

    /**
     * Select which view to display based on the state of the promotion. Will return the form if user selects to perform
     * promotion. Progress will be returned if the promotion is currently in progress.
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public void doIndex(StaplerRequest req, StaplerResponse resp) throws IOException, ServletException {
        req.getView(this, chooseAction()).forward(req, resp);
    }

    private synchronized String chooseAction() {
        return workerThread == null ? "form.jelly" : "progress.jelly";
    }

    /**
     * Form submission is calling this method
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public void doSubmit(StaplerRequest req, StaplerResponse resp) throws IOException, ServletException {
        getACL().checkPermission(getPermission());

        req.bindParameters(this);

        new StageWorkerThread().start();

        resp.sendRedirect(".");
    }

    /**
     * The thread that performs the promotion asynchronously.
     */
    public final class StageWorkerThread extends TaskThread {

        public StageWorkerThread() {
            super(StageBuildAction.this, ListenerAndText.forMemory(null));
        }

        @Override
        protected void perform(TaskListener listener) {
            ArtifactoryBuildInfoClient client = null;
            try {
                listener.getLogger().println("Promoting build ....");
                ArtifactoryRedeployPublisher artifactoryPublisher = ActionableHelper.getPublisher(
                        build.getProject(), ArtifactoryRedeployPublisher.class);
                ArtifactoryServer server = artifactoryPublisher.getArtifactoryServer();

                Credentials deployer = CredentialResolver.getPreferredDeployer(artifactoryPublisher, server);
                client = server.createArtifactoryClient(deployer.getUsername(), deployer.getPassword());

                Cause.UserCause userCause = ActionableHelper.getUserCause(build);
                String username = null;
                if (userCause != null) {
                    username = userCause.getUserName();
                }

                // do a dry run first
                PromotionBuilder promotionBuilder = new PromotionBuilder()
                        .status(targetStatus)
                        .comment(comment)
                        .ciUser(username)
                        .targetRepo(repositoryKey)
                        .dependencies(includeDependencies)
                        .copy(useCopy)
                        .dryRun(true);
                listener.getLogger().println("Performing dry run staging (no changes are made during dry run) ...");
                HttpResponse dryResponse = client.stageBuild(build.getParent().getDisplayName(),
                        build.getNumber() + "", promotionBuilder.build());
                if (checkSuccess(dryResponse, true, listener)) {
                    listener.getLogger().println("Dry run finished successfully.\nPerforming staging ...");
                    HttpResponse wetResponse = client.stageBuild(build.getParent().getDisplayName(),
                            build.getNumber() + "", promotionBuilder.dryRun(false).build());
                    if (checkSuccess(wetResponse, false, listener)) {
                        listener.getLogger().println("Staging completed successfully!");
                    }
                }

                build.save();
                workerThread = null;
            } catch (Throwable e) {
                e.printStackTrace(listener.error(e.getMessage()));
            } finally {
                if (client != null) {
                    client.shutdown();
                }
            }
        }

        private boolean checkSuccess(HttpResponse response, boolean dryRun, TaskListener listener) {
            StatusLine status = response.getStatusLine();
            if (status.getStatusCode() != 200) {
                if (dryRun) {
                    listener.error("Staging failed during dry run (no change in Artifactory was done): " + status);
                } else {
                    listener.error("Staging failed. View Artifactory logs for more details: " + status);
                }
                return false;
            }

            boolean failed = false;
            try {
                HttpEntity entity = response.getEntity();
                InputStream is = entity.getContent();
                String content = IOUtils.toString(is, "UTF-8");
                JSONObject json = JSONObject.fromObject(content);
                JSONArray messages = json.getJSONArray("messages");
                for (Object messageObj : messages) {
                    JSONObject messageJson = (JSONObject) messageObj;
                    String level = messageJson.getString("level");
                    String message = messageJson.getString("message");
                    // TODO: we don't want to fail if no items were moved/copied. find a way to support it
                    if ((level.equals("WARNING") || level.equals("ERROR")) &&
                            !message.startsWith("No items were")) {
                        listener.error("Received " + level + ": " + message);
                        failed = true;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace(listener.error("Failed parsing staging response:"));
                failed = true;
            }

            return !failed;
        }
    }
}
