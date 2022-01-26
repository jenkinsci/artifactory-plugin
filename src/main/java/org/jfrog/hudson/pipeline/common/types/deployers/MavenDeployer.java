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
import org.jfrog.hudson.pipeline.action.DeployedMavenArtifactsAction;
import org.jfrog.hudson.pipeline.common.types.ArtifactoryServer;
import org.jfrog.hudson.util.publisher.PublisherContext;

import java.io.UnsupportedEncodingException;
import java.util.List;

/**
 * Created by Tamirh on 16/08/2016.
 */
public class MavenDeployer extends Deployer {
    private String snapshotRepo;
    private String releaseRepo;
    private boolean deployEvenIfUnstable = false;
    public static final MavenDeployer EMPTY_DEPLOYER;

    static {
        EMPTY_DEPLOYER = createEmptyDeployer();
    }

    @Whitelisted
    public String getReleaseRepo() {
        return releaseRepo;
    }

    @Whitelisted
    public MavenDeployer setReleaseRepo(String releaseRepo) {
        this.releaseRepo = releaseRepo;
        return this;
    }

    @Whitelisted
    public MavenDeployer setSnapshotRepo(String snapshotRepo) {
        this.snapshotRepo = snapshotRepo;
        return this;
    }

    @Whitelisted
    public String getSnapshotRepo() {
        return snapshotRepo;
    }

    @Whitelisted
    public Deployer setDeployEvenIfUnstable(boolean deployEvenIfUnstable) {
        this.deployEvenIfUnstable = deployEvenIfUnstable;
        return this;
    }

    /**
     * @return True if should deploy artifacts even when the build is unstable (test failures).
     */
    @Whitelisted
    public boolean isDeployEvenIfUnstable() {
        return deployEvenIfUnstable;
    }

    @Override
    @JsonIgnore
    public ServerDetails getDetails() {
        RepositoryConf snapshotRepositoryConf = new RepositoryConf(snapshotRepo, snapshotRepo, false);
        RepositoryConf releaseRepositoryConf = new RepositoryConf(releaseRepo, releaseRepo, false);
        return new ServerDetails(this.server.getServerName(), this.server.getUrl(), releaseRepositoryConf, snapshotRepositoryConf, releaseRepositoryConf, snapshotRepositoryConf, "", "");
    }

    @Override
    @JsonIgnore
    public PublisherContext.Builder getContextBuilder() throws UnsupportedEncodingException {
        return new PublisherContext.Builder().artifactoryServer(getArtifactoryServer())
                .deployerOverrider(this)
                .serverDetails(getDetails())
                .deployArtifacts(isDeployArtifacts())
                .threads(getThreads())
                .evenIfUnstable(isDeployEvenIfUnstable())
                .artifactoryPluginVersion(ActionableHelper.getArtifactoryPluginVersion())
                .includeEnvVars(isIncludeEnvVars())
                .skipBuildInfoDeploy(!isDeployBuildInfo())
                .deploymentProperties(DeploymentUrlUtils.buildMatrixParamsString(getProperties(), false))
                .includesExcludes(getArtifactsIncludeExcludeForDeyployment());
    }

    public boolean isEmpty() {
        return server == null || (StringUtils.isEmpty(releaseRepo) && StringUtils.isEmpty(snapshotRepo));
    }

    public String getTargetRepository(String deployPath) {
        return StringUtils.isNotBlank(snapshotRepo) && deployPath.contains("-SNAPSHOT") ? snapshotRepo : releaseRepo;
    }

    private static MavenDeployer createEmptyDeployer() {
        MavenDeployer dummy = new MavenDeployer();
        ArtifactoryServer server = new ArtifactoryServer("http://empty_url", "user", "password");
        dummy.setServer(server);
        dummy.setReleaseRepo("empty_repo");
        dummy.setSnapshotRepo("empty_repo");
        dummy.setDeployArtifacts(false);
        dummy.setDeployEvenIfUnstable(false);
        dummy.setThreads(1);
        return dummy;
    }

    /**
     * Adds the provided artifacts to the Deployed Maven Artifacts Summary Action.
     * If such action was not initialized yet, initialize a new one.
     */
    public static void addDeployedMavenArtifactsToAction(Run<?, ?> build, List<DeployedArtifact> mavenArtifacts) {
        synchronized (build.getAllActions()) {
            DeployedMavenArtifactsAction action = build.getAction(DeployedMavenArtifactsAction.class);
            // Initialize action if we haven't done so yet.
            if (action == null) {
                action = new DeployedMavenArtifactsAction(build);
                build.addAction(action);
            }
            action.appendDeployedArtifacts(mavenArtifacts);
        }
    }
}
