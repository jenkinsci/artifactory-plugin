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
import hudson.model.Descriptor;
import hudson.model.TaskAction;
import hudson.model.TaskListener;
import hudson.model.TaskThread;
import hudson.security.ACL;
import hudson.security.Permission;
import hudson.tasks.Publisher;
import hudson.util.DescribableList;
import org.jfrog.build.client.ArtifactoryBuildInfoClient;
import org.jfrog.hudson.ArtifactoryRedeployPublisher;
import org.jfrog.hudson.ArtifactoryServer;
import org.jfrog.hudson.util.CredentialResolver;
import org.jfrog.hudson.util.Credentials;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
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
        return "Stage in Artifactory";
    }

    public String getUrlName() {
        return "stage";
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
        ArtifactoryRedeployPublisher artifactoryPublisher = getArtifactoryPublisher();
        if (artifactoryPublisher != null) {
            return artifactoryPublisher.getArtifactoryServer().getReleaseRepositoryKeysFirst();
        } else {
            return Collections.emptyList();
        }
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public List<String> getTargetStatuses() {
        return Lists.newArrayList("Staged", "Released", "Rolled-back");
    }

    /**
     * @return The repository selected by the latest promotion (to be selected by default).
     */
    public String lastPromotionRepository() {
        // TODO: implement
        return null;
    }

    private ArtifactoryRedeployPublisher getArtifactoryPublisher() {
        DescribableList<Publisher, Descriptor<Publisher>> publishersList = build.getProject().getPublishersList();
        for (Publisher publisher : publishersList) {
            if (publisher instanceof ArtifactoryRedeployPublisher) {
                return ((ArtifactoryRedeployPublisher) publisher);
            }
        }
        return null;
    }

    /**
     * Select which view to display based on the state of the promotion. Will return the form if user selects to
     * perform promotion. Progress will be returned if the promotion is currently in progress.
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
        req.bindParameters(this);

        new StageWorkerThread().start();

        resp.sendRedirect(".");
    }

    @Override
    protected Permission getPermission() {
        return null;
    }

    @Override
    protected ACL getACL() {
        return build.getACL();
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

                ArtifactoryRedeployPublisher artifactoryPublisher = getArtifactoryPublisher();
                ArtifactoryServer server = artifactoryPublisher.getArtifactoryServer();

                Credentials deployer = CredentialResolver.getPreferredDeployer(artifactoryPublisher, server);
                client = server.createArtifactoryClient(deployer.getUsername(),
                        deployer.getPassword());

                //client.promoteBuild(build, repositoryKey);
                Thread.sleep(2000);

                build.save();
                workerThread = null;
            } catch (Throwable e) {
                e.printStackTrace(listener.fatalError(e.getMessage()));
            } finally {
                if (client != null) {
                    client.shutdown();
                }
            }
        }
    }
}
