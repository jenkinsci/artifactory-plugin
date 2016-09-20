package org.jfrog.hudson.pipeline.executors;

import com.fasterxml.jackson.databind.ObjectMapper;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import org.eclipse.jgit.util.StringUtils;
import org.jfrog.build.api.Dependency;
import org.jfrog.build.api.util.Log;
import org.jfrog.build.client.ProxyConfiguration;
import org.jfrog.build.extractor.clientConfiguration.client.ArtifactoryDependenciesClient;
import org.jfrog.build.extractor.clientConfiguration.util.AqlDependenciesHelper;
import org.jfrog.build.extractor.clientConfiguration.util.DependenciesHelper;
import org.jfrog.build.extractor.clientConfiguration.util.WildcardsDependenciesHelper;
import org.jfrog.hudson.ArtifactoryServer;
import org.jfrog.hudson.CredentialsConfig;
import org.jfrog.hudson.generic.DependenciesDownloaderImpl;
import org.jfrog.hudson.pipeline.Utils;
import org.jfrog.hudson.pipeline.json.DownloadUploadJson;
import org.jfrog.hudson.pipeline.json.FileJson;
import org.jfrog.hudson.pipeline.types.buildInfo.BuildInfo;
import org.jfrog.hudson.pipeline.types.buildInfo.BuildInfoAccessor;
import org.jfrog.hudson.util.JenkinsBuildInfoLog;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.List;

/**
 * Created by romang on 4/19/16.
 */
public class GenericDownloadExecutor {
    private final Run build;
    private transient FilePath ws;
    private BuildInfo buildInfo;
    private ArtifactoryServer server;
    private Log log;
    private TaskListener listener;

    public GenericDownloadExecutor(ArtifactoryServer server, TaskListener listener, Run build, FilePath ws, BuildInfo buildInfo) {
        this.server = server;
        this.listener = listener;
        this.log = new JenkinsBuildInfoLog(listener);
        this.build = build;
        this.buildInfo = Utils.prepareBuildinfo(build, buildInfo);
        this.ws = ws;
    }

    public BuildInfo execution(String json) throws IOException, InterruptedException {
        ObjectMapper mapper = new ObjectMapper();
        DownloadUploadJson downloadJson = mapper.readValue(json, DownloadUploadJson.class);
        downloadArtifacts(downloadJson);
        return this.buildInfo;
    }

    private void downloadArtifacts(DownloadUploadJson downloadJson) throws IOException, InterruptedException {
        hudson.ProxyConfiguration proxy = Jenkins.getInstance().proxy;
        ProxyConfiguration proxyConfiguration = null;
        if (proxy != null) {
            proxyConfiguration = new ProxyConfiguration();
            proxyConfiguration.host = proxy.name;
            proxyConfiguration.port = proxy.port;
            proxyConfiguration.username = proxy.getUserName();
            proxyConfiguration.password = proxy.getPassword();
        }

        CredentialsConfig preferredResolver = server.getDeployerCredentialsConfig();
        ArtifactoryDependenciesClient dependenciesClient = server.createArtifactoryDependenciesClient(
            preferredResolver.getUsername(), preferredResolver.getPassword(),
            proxyConfiguration, listener);

        DependenciesDownloaderImpl dependancyDownloader = new DependenciesDownloaderImpl(dependenciesClient, ws, log);
        AqlDependenciesHelper aqlHelper = new AqlDependenciesHelper(dependancyDownloader, server.getUrl(), "", log);
        WildcardsDependenciesHelper wildcardHelper = new WildcardsDependenciesHelper(dependancyDownloader, server.getUrl(), "", log);

        for (FileJson file : downloadJson.getFiles()) {
            if (file.getPattern() != null) {
                wildcardHelper.setTarget(file.getTarget());
                boolean isFlat = file.getFlat() != null && StringUtils.toBoolean(file.getFlat());
                wildcardHelper.setFlatDownload(isFlat);
                boolean isRecursive = file.getRecursive() != null && StringUtils.toBoolean(file.getRecursive());
                wildcardHelper.setRecursive(isRecursive);
                String props = file.getProps() == null ? "" : file.getProps();
                wildcardHelper.setProps(props);
                download(file.getPattern(), wildcardHelper);
            }
            if (file.getAql() != null) {
                aqlHelper.setTarget(file.getTarget());
                download(file.getAql(), aqlHelper);
            }
        }
    }

    private void download(String downloadStr, DependenciesHelper helper) throws IOException, InterruptedException {
        List<Dependency> resolvedDependencies = helper.retrievePublishedDependencies(downloadStr);
        new BuildInfoAccessor(this.buildInfo).appendPublishedDependencies(resolvedDependencies);
    }

    public void stop(@Nonnull Throwable throwable) throws Exception {

    }
}
