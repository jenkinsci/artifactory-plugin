package org.jfrog.hudson.pipeline.common.types.deployers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import hudson.model.Run;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.jfrog.build.extractor.clientConfiguration.util.DeploymentUrlUtils;
import org.jfrog.hudson.RepositoryConf;
import org.jfrog.hudson.ServerDetails;
import org.jfrog.hudson.action.ActionableHelper;
import org.jfrog.hudson.pipeline.action.DeployedArtifact;
import org.jfrog.hudson.pipeline.action.DeployedGradleArtifactsAction;
import org.jfrog.hudson.pipeline.common.Utils;
import org.jfrog.hudson.pipeline.common.types.GradlePublications;
import org.jfrog.hudson.util.publisher.PublisherContext;

import java.io.IOException;
import java.util.List;

/**
 * Created by Tamirh on 16/08/2016.
 */
public class GradleDeployer extends Deployer {
    private static final String repoValidationMessage = "The Deployer should be set with either 'repo' or both 'releaseRepo' and 'snapshotRepo'";
    private GradlePublications publications = new GradlePublications();
    private Boolean deployMavenDescriptors;
    private Boolean deployIvyDescriptors;
    private String ivyPattern = "[organisation]/[module]/ivy-[revision].xml";
    private String artifactPattern = "[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier]).[ext]";
    private boolean mavenCompatible = true;
    private String repo;
    private String releaseRepo;
    private String snapshotRepo;

    @Override
    @JsonIgnore
    public ServerDetails getDetails() throws IOException {
        validateRepositories();
        RepositoryConf snapshotRepositoryConf = null;
        RepositoryConf releaseRepositoryConf;
        if (StringUtils.isNotEmpty(repo)) {
            releaseRepositoryConf = new RepositoryConf(repo, repo, false);
        } else {
            releaseRepositoryConf = new RepositoryConf(releaseRepo, releaseRepo, false);
            snapshotRepositoryConf = new RepositoryConf(snapshotRepo, snapshotRepo, false);
        }

        return new ServerDetails(server.getServerName(), server.getUrl(), releaseRepositoryConf, snapshotRepositoryConf, releaseRepositoryConf, null, "", "");
    }

    @JsonIgnore
    private void validateRepositories() throws IOException {
        if (StringUtils.isNotEmpty(repo)) {
            if (StringUtils.isNotEmpty(releaseRepo) || StringUtils.isNotEmpty(snapshotRepo)) {
                throw new IOException(repoValidationMessage);
            }
            return;
        }

        if (StringUtils.isEmpty(releaseRepo) || StringUtils.isEmpty(snapshotRepo)) {
            throw new IOException(repoValidationMessage);
        }
    }

    @Whitelisted
    public Boolean isDeployMavenDescriptors() {
        return deployMavenDescriptors;
    }

    @Whitelisted
    public void setDeployMavenDescriptors(boolean deployMavenDescriptors) {
        this.deployMavenDescriptors = deployMavenDescriptors;
    }

    @Whitelisted
    public Boolean isDeployIvyDescriptors() {
        return deployIvyDescriptors;
    }

    @Whitelisted
    public void setDeployIvyDescriptors(boolean deployIvyDescriptors) {
        this.deployIvyDescriptors = deployIvyDescriptors;
    }

    @Whitelisted
    public String getIvyPattern() {
        return ivyPattern;
    }

    @Whitelisted
    public void setIvyPattern(String ivyPattern) {
        this.ivyPattern = ivyPattern;
    }

    @Whitelisted
    public String getArtifactPattern() {
        return artifactPattern;
    }

    @Whitelisted
    public void setArtifactPattern(String artifactPattern) {
        this.artifactPattern = artifactPattern;
    }

    @Whitelisted
    public boolean getMavenCompatible() {
        return mavenCompatible;
    }

    @Whitelisted
    public void setMavenCompatible(boolean mavenCompatible) {
        this.mavenCompatible = mavenCompatible;
    }

    @Whitelisted
    public boolean isMavenCompatible() {
        return mavenCompatible;
    }

    @Whitelisted
    public String getRepo() {
        return repo;
    }

    @Whitelisted
    public void setRepo(String repo) {
        this.repo = repo;
    }

    @Whitelisted
    public String getSnapshotRepo() {
        return snapshotRepo;
    }

    @Whitelisted
    public void setSnapshotRepo(String snapshotRepo) {
        this.snapshotRepo = snapshotRepo;
    }

    @Whitelisted
    public String getReleaseRepo() {
        return releaseRepo;
    }

    @Whitelisted
    public void setReleaseRepo(String releaseRepo) {
        this.releaseRepo = releaseRepo;
    }

    @Whitelisted
    public GradlePublications getPublications() {
        return this.publications;
    }

    @Whitelisted
    public void setPublications(GradlePublications publications) {
        this.publications = publications;
    }

    public boolean isEmpty() {
        return server == null || (StringUtils.isEmpty(repo) && StringUtils.isEmpty(snapshotRepo) &&
                StringUtils.isEmpty(releaseRepo));
    }

    public String getTargetRepository(String deployPath) {
        if (StringUtils.isNotBlank(snapshotRepo) && deployPath.contains("-SNAPSHOT")) {
            return snapshotRepo;
        }
        return StringUtils.isNotBlank(releaseRepo) ? releaseRepo : repo;
    }

    @JsonIgnore
    public PublisherContext.Builder getContextBuilder() throws IOException {
        return new PublisherContext.Builder()
                .artifactoryServer(getArtifactoryServer())
                .serverDetails(getDetails())
                .deployArtifacts(isDeployArtifacts()).includesExcludes(Utils.getArtifactsIncludeExcludeForDeyployment(getArtifactDeploymentPatterns().getPatternFilter()))
                .threads(getThreads())
                .skipBuildInfoDeploy(!isDeployBuildInfo())
                .artifactsPattern(getArtifactPattern())
                .ivyPattern(getIvyPattern())
                .deployIvy(isDeployIvyDescriptors())
                .deployMaven(isDeployMavenDescriptors())
                .deployerOverrider(this)
                .includeEnvVars(isIncludeEnvVars())
                .maven2Compatible(getMavenCompatible())
                .deploymentProperties(DeploymentUrlUtils.buildMatrixParamsString(getProperties(), false))
                .artifactoryPluginVersion(ActionableHelper.getArtifactoryPluginVersion())
                .publications(getPublications().getPublications());
    }

    /**
     * Adds the provided artifacts to the Deployed Gradle Artifacts Summary Action.
     * If such action was not initialized yet, initialize a new one.
     */
    public static void addDeployedGradleArtifactsToAction(Run<?, ?> build, List<DeployedArtifact> gradleArtifacts) {
        synchronized (build.getAllActions()) {
            DeployedGradleArtifactsAction action = build.getAction(DeployedGradleArtifactsAction.class);
            // Initialize action if we haven't done so yet.
            if (action == null) {
                action = new DeployedGradleArtifactsAction(build);
                build.addAction(action);
            }
            action.appendDeployedArtifacts(gradleArtifacts);
        }
    }
}
