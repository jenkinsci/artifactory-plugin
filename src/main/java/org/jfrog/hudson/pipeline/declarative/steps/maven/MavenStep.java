package org.jfrog.hudson.pipeline.declarative.steps.maven;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import hudson.Extension;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.workflow.steps.*;
import org.jfrog.hudson.pipeline.common.executors.MavenExecutor;
import org.jfrog.hudson.pipeline.common.types.ArtifactoryServer;
import org.jfrog.hudson.pipeline.ArtifactorySynchronousNonBlockingStepExecution;
import org.jfrog.hudson.pipeline.common.types.buildInfo.BuildInfo;
import org.jfrog.hudson.pipeline.common.types.builds.MavenBuild;
import org.jfrog.hudson.pipeline.common.types.deployers.Deployer;
import org.jfrog.hudson.pipeline.common.types.deployers.MavenDeployer;
import org.jfrog.hudson.pipeline.common.types.resolvers.MavenResolver;
import org.jfrog.hudson.pipeline.declarative.BuildDataFile;
import org.jfrog.hudson.pipeline.declarative.utils.DeclarativePipelineUtils;
import org.jfrog.hudson.util.BuildUniqueIdentifierHelper;
import org.jfrog.hudson.util.JenkinsBuildInfoLog;
import org.jfrog.hudson.util.PropertyUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;
import java.util.Objects;

import static org.jfrog.hudson.util.SerializationUtils.createMapper;

/**
 * Run Maven-Artifactory task.
 */
@SuppressWarnings("unused")
public class MavenStep extends AbstractStepImpl {
    static final String STEP_NAME = "rtMavenRun";
    private final MavenBuild mavenBuild;
    private final String goals;
    private final String pom;
    private String customBuildNumber;
    private String customBuildName;
    private String project;
    private String deployerId;
    private String resolverId;

    @DataBoundConstructor
    public MavenStep(String pom, String goals) {
        mavenBuild = new MavenBuild();
        this.goals = Objects.toString(goals, "");
        this.pom = Objects.toString(pom, "");
    }

    @DataBoundSetter
    public void setBuildNumber(String customBuildNumber) {
        this.customBuildNumber = customBuildNumber;
    }

    @DataBoundSetter
    public void setBuildName(String customBuildName) {
        this.customBuildName = customBuildName;
    }

    @DataBoundSetter
    public void setProject(String customProject) {
        this.project = customProject;
    }

    @DataBoundSetter
    public void setDeployerId(String deployerId) {
        this.deployerId = deployerId;
    }

    @DataBoundSetter
    public void setResolverId(String resolverId) {
        this.resolverId = resolverId;
    }

    @DataBoundSetter
    public void setTool(String tool) {
        mavenBuild.setTool(tool);
    }

    @DataBoundSetter
    public void setUseWrapper(boolean useWrapper) {
        mavenBuild.setUseWrapper(useWrapper);
    }

    @DataBoundSetter
    public void setOpts(String opts) {
        mavenBuild.setOpts(opts);
    }

    public static class Execution extends ArtifactorySynchronousNonBlockingStepExecution<Void> {

        private transient final MavenStep step;

        @Inject
        public Execution(MavenStep step, StepContext context) throws IOException, InterruptedException {
            super(context);
            this.step = step;
        }

        @Override
        protected Void runStep() throws Exception {
            BuildInfo buildInfo = DeclarativePipelineUtils.getBuildInfo(rootWs, build, step.customBuildName, step.customBuildNumber, step.project);
            setMavenBuild();
            MavenExecutor mavenExecutor = new MavenExecutor(listener, launcher, build, ws, env, step.mavenBuild, step.pom, step.goals, buildInfo);
            mavenExecutor.execute();
            buildInfo = mavenExecutor.getBuildInfo();
            DeclarativePipelineUtils.saveBuildInfo(buildInfo, rootWs, build, new JenkinsBuildInfoLog(listener));
            return null;
        }

        @Override
        public org.jfrog.hudson.ArtifactoryServer getUsageReportServer() throws IOException, InterruptedException {
            Deployer deployer = step.mavenBuild.getDeployer();
            if (deployer != null) {
                return deployer.getArtifactoryServer();
            }
            MavenResolver resolver = step.mavenBuild.getResolver();
            if (resolver != null) {
                return resolver.getArtifactoryServer();
            }
            return null;
        }

        @Override
        public String getUsageReportFeatureName() {
            return STEP_NAME;
        }

        private void setMavenBuild() throws IOException, InterruptedException {
            String buildNumber = BuildUniqueIdentifierHelper.getBuildNumber(build);
            setDeployer(buildNumber);
            setResolver(buildNumber);
        }

        private void setDeployer(String buildNumber) throws IOException, InterruptedException {
            if (StringUtils.isBlank(step.deployerId)) {
                return;
            }
            BuildDataFile buildDataFile = DeclarativePipelineUtils.readBuildDataFile(rootWs, buildNumber, MavenDeployerStep.STEP_NAME, step.deployerId);
            if (buildDataFile == null) {
                throw new IOException("Deployer " + step.deployerId + " doesn't exist!");
            }
            MavenDeployer deployer = createMapper().treeToValue(buildDataFile.get(MavenDeployerStep.STEP_NAME), MavenDeployer.class);
            deployer.setServer(getArtifactoryServer(buildNumber, buildDataFile));
            step.mavenBuild.setDeployer(deployer);
            addProperties(buildDataFile);
        }

        private void addProperties(BuildDataFile buildDataFile) {
            JsonNode propertiesNode = buildDataFile.get("properties");
            if (propertiesNode != null) {
                step.mavenBuild.getDeployer().getProperties().putAll(PropertyUtils.getDeploymentPropertiesMap(propertiesNode.asText(), env));
            }
        }

        private void setResolver(String buildNumber) throws IOException, InterruptedException {
            if (StringUtils.isBlank(step.resolverId)) {
                return;
            }
            BuildDataFile buildDataFile = DeclarativePipelineUtils.readBuildDataFile(rootWs, buildNumber, MavenResolverStep.STEP_NAME, step.resolverId);
            if (buildDataFile == null) {
                throw new IOException("Resolver " + step.resolverId + " doesn't exist!");
            }
            MavenResolver resolver = createMapper().treeToValue(buildDataFile.get(MavenResolverStep.STEP_NAME), MavenResolver.class);
            resolver.setServer(getArtifactoryServer(buildNumber, buildDataFile));
            step.mavenBuild.setResolver(resolver);
        }

        private ArtifactoryServer getArtifactoryServer(String buildNumber, BuildDataFile buildDataFile) throws IOException, InterruptedException {
            JsonNode serverId = buildDataFile.get("serverId");
            if (serverId.isNull()) {
                throw new IllegalArgumentException("server ID is missing");
            }
            return DeclarativePipelineUtils.getArtifactoryServer(build, rootWs, serverId.asText(), true);
        }
    }

    @Extension
    public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(MavenStep.Execution.class);
        }

        @Override
        public String getFunctionName() {
            return "rtMavenRun";
        }

        @Override
        public String getDisplayName() {
            return "run Artifactory maven";
        }

        @Override
        public boolean isAdvanced() {
            return true;
        }
    }
}
