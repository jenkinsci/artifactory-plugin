package org.jfrog.hudson.pipeline;

import com.google.common.collect.Lists;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.apache.commons.lang.StringUtils;
import org.jfrog.build.api.*;
import org.jfrog.build.api.builder.BuildInfoBuilder;
import org.jfrog.build.api.builder.ModuleBuilder;
import org.jfrog.build.extractor.clientConfiguration.client.ArtifactoryBuildInfoClient;
import org.jfrog.hudson.AbstractBuildInfoDeployer;
import org.jfrog.hudson.BuildInfoResultAction;
import org.jfrog.hudson.pipeline.types.PipelineBuildInfoAccessor;
import org.jfrog.hudson.util.ExtractorUtils;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
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
    private PipelineBuildInfoAccessor accessor;

    public PipelineBuildInfoDeployer(ArtifactoryPipelineConfigurator configurator, ArtifactoryBuildInfoClient client
            , Run build, TaskListener listener, PipelineBuildInfoAccessor pipelineBuildinfoAccessor) throws IOException, InterruptedException, NoSuchAlgorithmException {
        super(configurator, build, listener, client);
        this.configurator = configurator;
        this.build = build;
        this.envVars = pipelineBuildinfoAccessor.getEnvVars();
        this.sysVars = pipelineBuildinfoAccessor.getSysVars();
        this.buildInfo = createBuildInfo("Pipeline", "Pipeline", BuildType.GENERIC);

        if(pipelineBuildinfoAccessor.getStartDate() != null) {
            this.buildInfo.setStartedDate(pipelineBuildinfoAccessor.getStartDate());
        }

        createDeployDetailsAndAddToBuildInfo(new ArrayList<Artifact>(pipelineBuildinfoAccessor.getDeployedArtifacts().values()), new ArrayList<Dependency>(pipelineBuildinfoAccessor.getPublishedDependencies().values()));
        buildInfo.setBuildDependencies(pipelineBuildinfoAccessor.getBuildDependencies());

        if (StringUtils.isNotEmpty(pipelineBuildinfoAccessor.getBuildName())) {
            buildInfo.setName(pipelineBuildinfoAccessor.getBuildName());
        }

        if (StringUtils.isNotEmpty(pipelineBuildinfoAccessor.getBuildNumber())) {
            buildInfo.setNumber(pipelineBuildinfoAccessor.getBuildNumber());
        }
    }

    public void deploy() throws IOException {
        String artifactoryUrl = configurator.getArtifactoryServer().getUrl();
        listener.getLogger().println("Deploying build info to: " + artifactoryUrl + "/api/build");
        client.sendBuildInfo(this.buildInfo);
        build.getActions().add(0, new BuildInfoResultAction(artifactoryUrl, build, this.buildInfo));
    }

    private void createDeployDetailsAndAddToBuildInfo(List<Artifact> deployedArtifacts,
                                                      List<Dependency> publishedDependencies) throws IOException, NoSuchAlgorithmException {
        ModuleBuilder moduleBuilder = new ModuleBuilder()
                .id(ExtractorUtils.sanitizeBuildName(build.getParent().getDisplayName()))
                .artifacts(deployedArtifacts);
        moduleBuilder.dependencies(publishedDependencies);
        buildInfo.setModules(Lists.newArrayList(moduleBuilder.build()));
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
