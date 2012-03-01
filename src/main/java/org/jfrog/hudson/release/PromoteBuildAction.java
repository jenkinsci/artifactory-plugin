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

package org.jfrog.hudson.release;

import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import hudson.model.AbstractBuild;
import hudson.model.BuildBadgeAction;
import hudson.model.TaskAction;
import hudson.model.TaskListener;
import hudson.model.TaskThread;
import hudson.model.User;
import hudson.security.ACL;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.jfrog.build.api.builder.PromotionBuilder;
import org.jfrog.build.client.ArtifactoryBuildInfoClient;
import org.jfrog.hudson.ArtifactoryRedeployPublisher;
import org.jfrog.hudson.ArtifactoryServer;
import org.jfrog.hudson.PluginSettings;
import org.jfrog.hudson.UserPluginInfo;
import org.jfrog.hudson.action.ActionableHelper;
import org.jfrog.hudson.util.Credentials;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * This badge action is added to a successful staged builds. It allows performing additional promotion.
 *
 * @author Yossi Shaul
 */
public abstract class PromoteBuildAction extends TaskAction implements BuildBadgeAction {
    private final AbstractBuild build;

    private String targetStatus;
    private String repositoryKey;
    private String comment;
    private boolean useCopy;
    private boolean includeDependencies;
    private PluginSettings promotionPlugin;

    public PromoteBuildAction(AbstractBuild build) {
        this.build = build;
    }

    public String getIconFileName() {
        return "/plugin/artifactory/images/artifactory-release.png";
    }

    public String getDisplayName() {
        return "Artifactory Release Promotion";
    }

    public String getUrlName() {
        if (hasPromotionPermission()) {
            return "promote";
        }
        // return null to hide this action
        return null;
    }

    public boolean hasPromotionPermission() {
        return getACL().hasPermission(getPermission());
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

    public String getPromotionPluginName() {
        return (promotionPlugin != null) ? promotionPlugin.getPluginName() : null;
    }

    public void setPromotionPlugin(PluginSettings promotionPlugin) {
        this.promotionPlugin = promotionPlugin;
    }

    public String getPluginParamValue(String pluginName, String paramKey) {
        return (promotionPlugin != null) ? promotionPlugin.getPluginParamValue(pluginName, paramKey) : null;
    }

    /**
     * @return List of target repositories for deployment (release repositories first). Called from the UI.
     */
    public abstract List<String> getRepositoryKeys();

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

    /**
     * Form submission is calling this method
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public void doSubmit(StaplerRequest req, StaplerResponse resp) throws IOException, ServletException {
        getACL().checkPermission(getPermission());

        req.bindParameters(this);

        JSONObject formData = req.getSubmittedForm();
        if (formData.has("promotionPlugin")) {
            JSONObject pluginSettings = formData.getJSONObject("promotionPlugin");
            String pluginName = pluginSettings.getString("pluginName");
            if (!UserPluginInfo.NO_PLUGIN_KEY.equals(pluginName)) {
                PluginSettings settings = new PluginSettings();
                Map<String, String> paramMap = Maps.newHashMap();
                settings.setPluginName(pluginName);
                Map<String, Object> filteredPluginSettings = Maps.filterKeys(pluginSettings,
                        new Predicate<String>() {
                            public boolean apply(String input) {
                                return StringUtils.isNotBlank(input) && !"pluginName".equals(input);
                            }
                        });
                for (Map.Entry<String, Object> settingsEntry : filteredPluginSettings.entrySet()) {
                    String key = settingsEntry.getKey();
                    paramMap.put(key, pluginSettings.getString(key));
                }
                if (!paramMap.isEmpty()) {
                    settings.setParamMap(paramMap);
                }
                setPromotionPlugin(settings);
            }
        }

        ArtifactoryRedeployPublisher artifactoryPublisher = ActionableHelper.getPublisher(
                build.getProject(), ArtifactoryRedeployPublisher.class);
        ArtifactoryServer server = getArtifactoryServer();

        new PromoteWorkerThread(server, getCredentials(server)).start();

        resp.sendRedirect(".");
    }

    public List<UserPluginInfo> getPromotionsUserPluginInfo() {
        return getArtifactoryServer().getPromotionsUserPluginInfo();
    }

    /**
     * @return The Artifactory server that is used for the build.
     */
    protected abstract ArtifactoryServer getArtifactoryServer();

    /**
     * @param server The Artifactory server that is used for the build.
     * @return The credentials that were used for this server.
     */
    protected abstract Credentials getCredentials(ArtifactoryServer server);

    @Override
    protected ACL getACL() {
        return build.getACL();
    }

    private synchronized String chooseAction() {
        return workerThread == null ? "form.jelly" : "progress.jelly";
    }

    /**
     * The thread that performs the promotion asynchronously.
     */
    public final class PromoteWorkerThread extends TaskThread {

        private final ArtifactoryServer artifactoryServer;
        private final Credentials deployer;
        private final String ciUser;

        public PromoteWorkerThread(ArtifactoryServer artifactoryServer, Credentials deployer) {
            super(PromoteBuildAction.this, ListenerAndText.forMemory(null));
            this.artifactoryServer = artifactoryServer;
            this.deployer = deployer;
            // current user is bound to the thread and will be lost in the perform method
            User user = User.current();
            this.ciUser = (user == null) ? "anonymous" : user.getId();

        }

        @Override
        protected void perform(TaskListener listener) {
            ArtifactoryBuildInfoClient client = null;
            try {
                long started = System.currentTimeMillis();
                listener.getLogger().println("Promoting build ....");

                client = artifactoryServer.createArtifactoryClient(deployer.getUsername(), deployer.getPassword());

                if ((promotionPlugin != null) &&
                        !UserPluginInfo.NO_PLUGIN_KEY.equals(promotionPlugin.getPluginName())) {
                    handlePluginPromotion(listener, client);
                } else {
                    handleStandardPromotion(listener, client);

                }

                build.save();
                // if the client gets back to the progress (after the redirect) page when this thread already done,
                // she will get an error message because the log dies with the thread. So lets delay up to 3 seconds
                long timeToWait = 2000 - (System.currentTimeMillis() - started);
                if (timeToWait > 0) {
                    Thread.sleep(timeToWait);
                }
                workerThread = null;
            } catch (Throwable e) {
                e.printStackTrace(listener.error(e.getMessage()));
            } finally {
                if (client != null) {
                    client.shutdown();
                }
            }
        }

        private void handlePluginPromotion(TaskListener listener, ArtifactoryBuildInfoClient client)
                throws IOException {
            String buildName = build.getParent().getDisplayName();
            String buildNumber = build.getNumber() + "";
            HttpResponse pluginPromotionResponse = client.executePromotionUserPlugin(
                    promotionPlugin.getPluginName(), buildName, buildNumber, promotionPlugin.getParamMap());
            if (checkSuccess(pluginPromotionResponse, false, false, listener)) {
                listener.getLogger().println("Promotion completed successfully!");
            }
        }

        private void handleStandardPromotion(TaskListener listener, ArtifactoryBuildInfoClient client)
                throws IOException {
            // do a dry run first
            PromotionBuilder promotionBuilder = new PromotionBuilder()
                    .status(targetStatus)
                    .comment(comment)
                    .ciUser(ciUser)
                    .targetRepo(repositoryKey)
                    .dependencies(includeDependencies)
                    .copy(useCopy)
                    .dryRun(true);
            listener.getLogger()
                    .println("Performing dry run promotion (no changes are made during dry run) ...");
            String buildName = build.getParent().getDisplayName();
            String buildNumber = build.getNumber() + "";
            HttpResponse dryResponse = client.stageBuild(buildName, buildNumber, promotionBuilder.build());
            if (checkSuccess(dryResponse, true, true, listener)) {
                listener.getLogger().println("Dry run finished successfully.\nPerforming promotion ...");
                HttpResponse wetResponse = client.stageBuild(buildName,
                        buildNumber, promotionBuilder.dryRun(false).build());
                if (checkSuccess(wetResponse, false, true, listener)) {
                    listener.getLogger().println("Promotion completed successfully!");
                }
            }
        }

        /**
         * Checks the status and return true on success
         *
         * @param response
         * @param dryRun
         * @param parseMessages
         * @param listener
         * @return
         */
        private boolean checkSuccess(HttpResponse response, boolean dryRun, boolean parseMessages,
                TaskListener listener) {
            StatusLine status = response.getStatusLine();
            try {
                String content = entityToString(response);
                if (assertResponseStatus(dryRun, listener, status, content)) {
                    if (parseMessages) {
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
                                return false;
                            }
                        }
                    }
                    return true;
                }
            } catch (IOException e) {
                e.printStackTrace(listener.error("Failed parsing promotion response:"));
            }
            return false;
        }

        private boolean assertResponseStatus(boolean dryRun, TaskListener listener, StatusLine status, String content) {
            if (status.getStatusCode() != 200) {
                if (dryRun) {
                    listener.error(
                            "Promotion failed during dry run (no change in Artifactory was done): " + status +
                                    "\n" + content);
                } else {
                    listener.error(
                            "Promotion failed. View Artifactory logs for more details: " + status + "\n" + content);
                }
                return false;
            }
            return true;
        }

        private String entityToString(HttpResponse response) throws IOException {
            HttpEntity entity = response.getEntity();
            InputStream is = entity.getContent();
            return IOUtils.toString(is, "UTF-8");
        }
    }
}
