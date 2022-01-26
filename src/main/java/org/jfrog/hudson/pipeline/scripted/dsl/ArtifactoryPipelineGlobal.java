package org.jfrog.hudson.pipeline.scripted.dsl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.jenkinsci.plugins.workflow.cps.CpsScript;
import org.jfrog.hudson.pipeline.common.Utils;
import org.jfrog.hudson.pipeline.common.types.ArtifactoryServer;
import org.jfrog.hudson.pipeline.common.types.ConanClient;
import org.jfrog.hudson.pipeline.common.types.Docker;
import org.jfrog.hudson.pipeline.common.types.MavenDescriptor;
import org.jfrog.hudson.pipeline.common.types.buildInfo.BuildInfo;
import org.jfrog.hudson.pipeline.common.types.builds.*;

import java.io.Serializable;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Tamirh on 18/05/2016.
 */
public class ArtifactoryPipelineGlobal implements Serializable {
    private CpsScript cpsScript;

    public ArtifactoryPipelineGlobal(CpsScript script) {
        this.cpsScript = script;
    }

    @Whitelisted
    public ArtifactoryServer server(String serverName) {
        Map<String, Object> stepVariables = new LinkedHashMap<>();
        stepVariables.put("artifactoryServerID", serverName);
        ArtifactoryServer server = (ArtifactoryServer) cpsScript.invokeMethod("getArtifactoryServer", stepVariables);
        server.setCpsScript(cpsScript);
        return server;
    }

    @Whitelisted
    public Docker docker(ArtifactoryServer server) {
        return docker(server, "");
    }

    @Whitelisted
    public Docker docker(ArtifactoryServer server, String host) {
        Map<String, Object> dockerArguments = new LinkedHashMap<>();
        dockerArguments.put("server", server);
        if (host != null && !host.isEmpty()) {
            dockerArguments.put("host", host);
        }
        return docker(dockerArguments);
    }

    @Whitelisted
    public Docker docker(Map<String, Object> dockerArguments) {
        List<String> keysAsList = Arrays.asList("server", "host", "javaArgs");
        if (!keysAsList.containsAll(dockerArguments.keySet())) {
            throw new IllegalArgumentException("Only the following arguments are allowed: " + keysAsList);
        }

        // We don't want to handle the deserialization of the ArtifactoryServer.
        // Instead we will remove it and later on set it on the deployer object.
        Object server = dockerArguments.remove("server");

        final ObjectMapper mapper = new ObjectMapper();
        Docker docker = mapper.convertValue(dockerArguments, Docker.class);
        docker.setCpsScript(cpsScript);
        if (server != null) {
            docker.setServer((ArtifactoryServer) server);
        }
        return docker;
    }

    @Whitelisted
    public ArtifactoryServer newServer(String url, String username, String password) {
        Map<String, Object> stepVariables = new LinkedHashMap<>();
        stepVariables.put("url", url);
        stepVariables.put("username", username);
        stepVariables.put("password", password);
        ArtifactoryServer server = (ArtifactoryServer) cpsScript.invokeMethod("newArtifactoryServer", stepVariables);
        server.setCpsScript(cpsScript);
        return server;
    }

    @Whitelisted
    public ArtifactoryServer newServer(Map<String, Object> serverArguments) {
        List<String> keysAsList = Arrays.asList("url", "username", "password", "credentialsId");
        if (!keysAsList.containsAll(serverArguments.keySet())) {
            throw new IllegalArgumentException("The newServer method accepts the following arguments only: " + keysAsList);
        }

        ArtifactoryServer server = (ArtifactoryServer) cpsScript.invokeMethod("newArtifactoryServer", serverArguments);
        server.setCpsScript(cpsScript);
        return server;
    }

    @Whitelisted
    public BuildInfo newBuildInfo() {
        BuildInfo buildInfo = (BuildInfo) cpsScript.invokeMethod("newBuildInfo", new LinkedHashMap<>());
        buildInfo.setCpsScript(cpsScript);
        return buildInfo;
    }

    @Whitelisted
    public BuildInfo newBuildInfo(String buildName, String buildNumber) {
        return newBuildInfo(buildName, buildNumber, null);
    }

    @Whitelisted
    public BuildInfo newBuildInfo(String buildName, String buildNumber, String project) {
        BuildInfo buildInfo = newBuildInfo();
        buildInfo.setName(buildName);
        buildInfo.setNumber(buildNumber);
        buildInfo.setProject(project);
        return buildInfo;
    }

    @Whitelisted
    public MavenBuild newMavenBuild() {
        MavenBuild mavenBuild = (MavenBuild) cpsScript.invokeMethod("newMavenBuild", new LinkedHashMap<>());
        mavenBuild.setCpsScript(cpsScript);
        return mavenBuild;
    }

    @Whitelisted
    public GradleBuild newGradleBuild() {
        GradleBuild gradleBuild = (GradleBuild) cpsScript.invokeMethod("newGradleBuild", new LinkedHashMap<>());
        gradleBuild.setCpsScript(cpsScript);
        return gradleBuild;
    }

    @Whitelisted
    public NpmBuild newNpmBuild() {
        NpmBuild npmBuild = (NpmBuild) cpsScript.invokeMethod("newNpmBuild", new LinkedHashMap<>());
        npmBuild.setCpsScript(cpsScript);
        return npmBuild;
    }

    @Whitelisted
    public GoBuild newGoBuild() {
        GoBuild goBuild = (GoBuild) cpsScript.invokeMethod("newGoBuild", new LinkedHashMap<>());
        goBuild.setCpsScript(cpsScript);
        return goBuild;
    }

    @Whitelisted
    public PipBuild newPipBuild() {
        PipBuild pipBuild = (PipBuild) cpsScript.invokeMethod("newPipBuild", new LinkedHashMap<>());
        pipBuild.setCpsScript(cpsScript);
        return pipBuild;
    }

    @Whitelisted
    public NugetBuild newNugetBuild() {
        NugetBuild nugetBuild = (NugetBuild) cpsScript.invokeMethod("newNugetBuild", new LinkedHashMap<>());
        nugetBuild.setCpsScript(cpsScript);
        return nugetBuild;
    }

    @Whitelisted
    public NugetBuild newDotnetBuild() {
        NugetBuild dotnetCliBuild = (NugetBuild) cpsScript.invokeMethod("newNugetBuild", new LinkedHashMap<>());
        dotnetCliBuild.setCpsScript(cpsScript);
        dotnetCliBuild.SetUseDotnetCli(true);
        return dotnetCliBuild;
    }

    @Whitelisted
    public ConanClient newConanClient(Map<String, Object> clientArgs) {
        ConanClient client = new ConanClient();
        String userPath = (String) clientArgs.get("userHome");
        if (StringUtils.isBlank(userPath)) {
            throw new IllegalArgumentException("The newConanClient method expects the 'userHome' argument or no arguments.");
        }
        client.setUserPath(userPath);
        client.setCpsScript(cpsScript);
        LinkedHashMap<String, Object> args = new LinkedHashMap<>();
        args.put("client", client);
        cpsScript.invokeMethod("initConanClient", args);
        return client;
    }

    @Whitelisted
    public ConanClient newConanClient() {
        ConanClient client = new ConanClient();
        client.setCpsScript(cpsScript);
        LinkedHashMap<String, Object> args = new LinkedHashMap<>();
        args.put("client", client);
        cpsScript.invokeMethod("initConanClient", args);
        return client;
    }

    @Whitelisted
    public MavenDescriptor mavenDescriptor() {
        MavenDescriptor descriptorHandler = new MavenDescriptor();
        descriptorHandler.setCpsScript(cpsScript);
        return descriptorHandler;
    }

    @Whitelisted
    public void addInteractivePromotion(Map<String, Object> promotionArguments) {
        Map<String, Object> stepVariables = new LinkedHashMap<>();
        List<String> mandatoryParams = Arrays.asList(ArtifactoryServer.SERVER, "promotionConfig");
        List<String> allowedParams = Arrays.asList(ArtifactoryServer.SERVER, "promotionConfig", "displayName");
        if (!promotionArguments.keySet().containsAll(mandatoryParams)) {
            throw new IllegalArgumentException(mandatoryParams.toString() + " are mandatory arguments");
        }
        if (!allowedParams.containsAll(promotionArguments.keySet())) {
            throw new IllegalArgumentException("Only the following arguments are allowed: " + allowedParams.toString());
        }
        stepVariables.put("promotionConfig", Utils.createPromotionConfig((Map<String, Object>) promotionArguments.get("promotionConfig"), false));
        stepVariables.put(ArtifactoryServer.SERVER, promotionArguments.get(ArtifactoryServer.SERVER));
        stepVariables.put("displayName", promotionArguments.get("displayName"));
        cpsScript.invokeMethod("addInteractivePromotion", stepVariables);
    }
}
