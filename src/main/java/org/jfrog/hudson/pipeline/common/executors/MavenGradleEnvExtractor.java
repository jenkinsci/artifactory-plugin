package org.jfrog.hudson.pipeline.common.executors;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jfrog.build.api.BuildInfoConfigProperties;
import org.jfrog.build.api.BuildInfoFields;
import org.jfrog.build.extractor.clientConfiguration.ArtifactoryClientConfiguration;
import org.jfrog.hudson.CredentialsConfig;
import org.jfrog.hudson.action.ActionableHelper;
import org.jfrog.hudson.pipeline.common.Utils;
import org.jfrog.hudson.pipeline.common.types.buildInfo.BuildInfo;
import org.jfrog.hudson.pipeline.common.types.deployers.Deployer;
import org.jfrog.hudson.pipeline.common.types.resolvers.Resolver;
import org.jfrog.hudson.release.ReleaseAction;
import org.jfrog.hudson.util.CredentialManager;
import org.jfrog.hudson.util.ExtractorUtils;
import org.jfrog.hudson.util.ResolverContext;
import org.jfrog.hudson.util.publisher.PublisherContext;

/**
 * Created by Tamirh on 04/08/2016.
 */
public class MavenGradleEnvExtractor implements Executor {

    private Deployer publisher;
    private Resolver resolver;
    private Run build;
    private BuildInfo buildInfo;
    private TaskListener buildListener;
    private Launcher launcher;
    private FilePath tempDir;
    private EnvVars env;

    public MavenGradleEnvExtractor(Run build, BuildInfo buildInfo, Deployer publisher, Resolver resolver, TaskListener buildListener, Launcher launcher, FilePath tempDir, EnvVars env) {
        this.build = build;
        this.buildInfo = buildInfo;
        this.buildListener = buildListener;
        this.publisher = publisher;
        this.resolver = resolver;
        this.launcher = launcher;
        this.tempDir = tempDir;
        this.env = env;
    }

    private PublisherContext createPublisherContext() {
        return publisher.getContextBuilder().build();
    }

    public void execute() {
        env.put(ExtractorUtils.EXTRACTOR_USED, "true");
        ReleaseAction release = ActionableHelper.getLatestAction(build, ReleaseAction.class);
        if (release != null) {
            release.addVars(env);
        }
        try {
            // publisher should never be null or empty
            PublisherContext publisherContext = createPublisherContext();
            ResolverContext resolverContext = null;
            if (resolver != null && !resolver.isEmpty()) {
                CredentialsConfig resolverCredentials = CredentialManager.getPreferredResolver(resolver,
                        resolver.getArtifactoryServer());
                resolverContext = new ResolverContext(resolver.getArtifactoryServer(), resolver.getResolverDetails(),
                        resolverCredentials.provideCredentials(build.getParent()), resolver);
            }

            ArtifactoryClientConfiguration configuration = ExtractorUtils.getArtifactoryClientConfiguration(
                    env, build, buildInfo, buildListener, publisherContext, resolverContext);
            addPipelineInfoToConfiguration(env, configuration, tempDir);
            ExtractorUtils.persistConfiguration(configuration, env, tempDir, launcher);
            String propertiesFilePath = configuration.getPropertiesFile();
            env.put(BuildInfoConfigProperties.PROP_PROPS_FILE, propertiesFilePath);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void addPipelineInfoToConfiguration(EnvVars env, ArtifactoryClientConfiguration configuration, FilePath tempDir) {
        String buildInfoTempFile;
        String deployableArtifactsFile;
        try {
            buildInfoTempFile = Utils.createTempJsonFile(launcher, BuildInfoFields.GENERATED_BUILD_INFO, tempDir.getRemote());
            deployableArtifactsFile = Utils.createTempJsonFile(launcher, BuildInfoFields.DEPLOYABLE_ARTIFACTS, tempDir.getRemote());
        } catch (Exception e) {
            buildListener.error("Failed while generating temp file. " + e.getMessage());
            build.setResult(Result.FAILURE);
            throw new Run.RunnerAbortedException();
        }
        env.put(BuildInfoFields.GENERATED_BUILD_INFO, buildInfoTempFile);
        configuration.info.setGeneratedBuildInfoFilePath(buildInfoTempFile);
        env.put(BuildInfoFields.DEPLOYABLE_ARTIFACTS, deployableArtifactsFile);
        configuration.info.setDeployableArtifactsFilePath(deployableArtifactsFile);
    }
}
