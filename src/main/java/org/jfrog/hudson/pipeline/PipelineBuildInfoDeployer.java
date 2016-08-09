package org.jfrog.hudson.pipeline;

import hudson.model.Run;
import hudson.model.TaskListener;
import org.apache.commons.lang.StringUtils;
import org.jfrog.build.api.Build;
import org.jfrog.build.api.BuildInfoProperties;
import org.jfrog.build.api.BuildType;
import org.jfrog.build.api.builder.BuildInfoBuilder;
import org.jfrog.build.extractor.clientConfiguration.client.ArtifactoryBuildInfoClient;
import org.jfrog.hudson.AbstractBuildInfoDeployer;
import org.jfrog.hudson.BuildInfoResultAction;
import org.jfrog.hudson.pipeline.types.BuildInfoAccessor;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

/**
 * Created by romang on 4/25/16.
 */
public class PipelineBuildInfoDeployer extends AbstractBuildInfoDeployer {

    private final Run build;
    private final Map<String, String> sysVars;
    private final Map<String, String> envVars;
    private ArtifactoryPipelineConfigurator configurator;
    private Build buildInfo;

    public PipelineBuildInfoDeployer(ArtifactoryPipelineConfigurator configurator, ArtifactoryBuildInfoClient client,
                                     Run build, TaskListener listener, BuildInfoAccessor buildinfoAccessor) throws IOException, InterruptedException, NoSuchAlgorithmException {
        super(configurator, build, listener, client);
        this.configurator = configurator;
        this.build = build;
        envVars = buildinfoAccessor.getEnvVars();
        sysVars = buildinfoAccessor.getSysVars();
        buildInfo = createBuildInfo("Pipeline", "Pipeline", BuildType.GENERIC);
        buildInfo.setBuildRetention(buildinfoAccessor.getRetention().build());

        if (buildinfoAccessor.getStartDate() != null) {
            buildInfo.setStartedDate(buildinfoAccessor.getStartDate());
        }

        buildInfo.setModules(buildinfoAccessor.getModules());
        this.buildInfo.setBuildDependencies(buildinfoAccessor.getBuildDependencies());

        if (StringUtils.isNotEmpty(buildinfoAccessor.getBuildName())) {
            buildInfo.setName(buildinfoAccessor.getBuildName());
        }

        if (StringUtils.isNotEmpty(buildinfoAccessor.getBuildNumber())) {
            buildInfo.setNumber(buildinfoAccessor.getBuildNumber());
        }
    }

    public void deploy() throws IOException {
        String artifactoryUrl = configurator.getArtifactoryServer().getUrl();
        listener.getLogger().println("Deploying build info to: " + artifactoryUrl + "/api/build");
        client.sendBuildInfo(this.buildInfo);
        build.getActions().add(0, new BuildInfoResultAction(artifactoryUrl, build, this.buildInfo));
    }

    /**
     * Adding environment and system variables to build info.
     *
     * @param builder
     */
    @Override
    protected void addBuildInfoProperties(BuildInfoBuilder builder) {
        if (envVars != null) {
            for (Map.Entry<String, String> entry : envVars.entrySet()) {
                builder.addProperty(BuildInfoProperties.BUILD_INFO_ENVIRONMENT_PREFIX + entry.getKey(), entry.getValue());
            }
        }

        if (sysVars != null) {
            for (Map.Entry<String, String> entry : sysVars.entrySet()) {
                builder.addProperty(entry.getKey(), entry.getValue());
            }
        }
    }
}
