package org.jfrog.hudson.pipeline.common.executors;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jfrog.build.api.Build;
import org.jfrog.build.api.util.Log;
import org.jfrog.build.extractor.clientConfiguration.ArtifactoryBuildInfoClientBuilder;
import org.jfrog.hudson.ArtifactoryServer;
import org.jfrog.hudson.CredentialsConfig;
import org.jfrog.hudson.go.GoRunCallable;
import org.jfrog.hudson.pipeline.common.Utils;
import org.jfrog.hudson.pipeline.common.types.resolvers.NpmGoResolver;
import org.jfrog.hudson.pipeline.common.types.buildInfo.BuildInfo;
import org.jfrog.hudson.pipeline.common.types.builds.GoBuild;

import org.jfrog.hudson.util.Credentials;
import org.jfrog.hudson.util.JenkinsBuildInfoLog;

import java.io.IOException;

public class GoRunExecutor implements Executor {

    private StepContext context;
    private BuildInfo buildInfo;
    private GoBuild goBuild;
    private FilePath ws;
    private String path;
    private String goCmdArgs;
    private Log logger;
    private EnvVars env;
    private Run build;

    public GoRunExecutor(StepContext context, BuildInfo buildInfo, GoBuild goBuild, String path, String goCmdArgs, FilePath ws, TaskListener listener, EnvVars env, Run build) {
        this.context = context;
        this.buildInfo = Utils.prepareBuildinfo(build, buildInfo);
        this.goBuild = goBuild;
        this.path = path;
        this.goCmdArgs = goCmdArgs;
        this.ws = ws;
        this.logger = new JenkinsBuildInfoLog(listener);
        this.env = env;
        this.build = build;
    }

    public BuildInfo getBuildInfo() {
        return buildInfo;
    }

    @Override
    public void execute() throws Exception {
        GoRunCallable runCallable = new GoRunCallable(path, goCmdArgs, logger, env);
        addResolverDetailsToCallable((NpmGoResolver) goBuild.getResolver(), runCallable);
        Build build = ws.act(runCallable);
        if (build == null) {
            throw new RuntimeException("go run failed");
        }
        buildInfo.append(build);
        buildInfo.setAgentName(Utils.getAgentName(ws));
    }

    private void addResolverDetailsToCallable(NpmGoResolver resolver, GoRunCallable callable) throws IOException {
        if (resolver.isEmpty()) {
            return;
        }
        ArtifactoryServer server = resolver.getArtifactoryServer();
        CredentialsConfig preferredResolver = server.getResolverCredentialsConfig();
        Credentials resolverCredentials = preferredResolver.provideCredentials(build.getParent());
        if (StringUtils.isNotEmpty(resolverCredentials.getAccessToken())) {
            resolverCredentials = resolverCredentials.convertAccessTokenToUsernamePassword();
        }
        String username = resolverCredentials.getUsername();
        String password = resolverCredentials.getPassword();
        ArtifactoryBuildInfoClientBuilder resolverClientBuilder = new ArtifactoryBuildInfoClientBuilder()
                .setArtifactoryUrl(server.getUrl())
                .setUsername(username)
                .setPassword(password)
                .setProxyConfiguration(ArtifactoryServer.createProxyConfiguration(Jenkins.getInstance().proxy))
                .setLog(logger)
                .setConnectionRetry(server.getConnectionRetry())
                .setConnectionTimeout(server.getTimeout());
        callable.setResolverDetails(resolverClientBuilder, resolver.getRepo(), username, password);
    }
}
