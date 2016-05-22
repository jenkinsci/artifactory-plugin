package org.jfrog.hudson.pipeline.dsl;

import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.jenkinsci.plugins.workflow.cps.CpsScript;
import org.jfrog.hudson.pipeline.types.ArtifactoryServer;
import org.jfrog.hudson.pipeline.types.BuildInfo;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by Tamirh on 18/05/2016.
 */
public class ArtifactoryPipelineGlobal implements Serializable {
    private org.jenkinsci.plugins.workflow.cps.CpsScript script;

    public ArtifactoryPipelineGlobal(CpsScript script) {
        this.script = script;
    }

    @Whitelisted
    public ArtifactoryServer server(String serverName) {
        Map<String, Object> stepVariables = new LinkedHashMap<String, Object>();
        stepVariables.put("artifactoryServerID", serverName);
        ArtifactoryServer server = (ArtifactoryServer) this.script.invokeMethod("getArtifactoryServer", stepVariables);
        server.setCpsScript(this.script);
        return server;
    }

    @Whitelisted
    public ArtifactoryServer newServer(String url, String userName, String password) {
        Map<String, Object> stepVariables = new LinkedHashMap<String, Object>();
        stepVariables.put("url", url);
        stepVariables.put("userName", userName);
        stepVariables.put("password", password);
        ArtifactoryServer server = (ArtifactoryServer) this.script.invokeMethod("newArtifactoryServer", stepVariables);
        server.setCpsScript(this.script);
        return server;
    }

    @Whitelisted
    public BuildInfo newBuildInfo() {
        BuildInfo buildInfo = (BuildInfo) this.script.invokeMethod("newBuildInfo", new LinkedHashMap<String, Object>());
        return buildInfo;
    }
}
