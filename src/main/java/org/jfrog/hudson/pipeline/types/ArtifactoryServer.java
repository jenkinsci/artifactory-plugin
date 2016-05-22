package org.jfrog.hudson.pipeline.types;

import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.jenkinsci.plugins.workflow.cps.CpsScript;
import org.jfrog.hudson.pipeline.PipelineBuildInfoDeployer;
import org.jfrog.hudson.pipeline.PipelineUtils;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by romang on 4/21/16.
 */
public class ArtifactoryServer implements Serializable {
    private String serverName;
    private String url;
    private String username;
    private String password;
    private boolean bypassProxy;
    private transient Run build;
    private transient TaskListener listener;

    private CpsScript cpsScript;

    public ArtifactoryServer(String artifactoryServerName, String url, String username, String password, Run build, TaskListener listener) {
        serverName = artifactoryServerName;
        this.url = url;
        this.username = username;
        this.password = password;
        this.build = build;
        this.listener = listener;
    }

    public ArtifactoryServer(String url, String username, String password, Run build, TaskListener listener) {
        this.url = url;
        this.username = username;
        this.password = password;
        this.build = build;
        this.listener = listener;
    }

    public void setCpsScript(CpsScript cpsScript) {
        this.cpsScript = cpsScript;
    }

    @Whitelisted
    public BuildInfo download(String json) throws Exception {
        return download(json, null);
    }

    @Whitelisted
    public BuildInfo download(String json, BuildInfo providedBuildInfo) throws Exception {
        Map<String, Object> stepVariables = new LinkedHashMap<String, Object>();
        stepVariables.put("json", json);
        stepVariables.put("providedBuildInfo", providedBuildInfo);
        stepVariables.put("server", this);
        return (BuildInfo) cpsScript.invokeMethod("artifactoryDownload", stepVariables);
    }

    @Whitelisted
    public BuildInfo upload(String json) throws Exception {
        return upload(json, null);
    }

    @Whitelisted
    public BuildInfo upload(String json, BuildInfo providedBuildInfo) throws Exception {
        Map<String, Object> stepVariables = new LinkedHashMap<String, Object>();
        stepVariables.put("json", json);
        stepVariables.put("providedBuildInfo", providedBuildInfo);
        stepVariables.put("server", this);
        return (BuildInfo) cpsScript.invokeMethod("artifactoryUpload", stepVariables);
    }

    @Whitelisted
    public void publishBuildInfo(BuildInfo buildInfo) throws Exception {
        TaskListener listener = getBuildListener();
        PipelineBuildInfoDeployer deployer = buildInfo.createDeployer(build, listener, PipelineUtils.prepareArtifactoryServer(null, this));
        deployer.deploy();
    }

    private TaskListener getBuildListener() {
        TaskListener listener;
        try {
            Field listenerField = build.getClass().getDeclaredField("listener");
            listenerField.setAccessible(true);
            listener = (StreamTaskListener) listenerField.get(build);
        } catch (NoSuchFieldException e) {
            Logger.getLogger(ArtifactoryServer.class.getName()).log(Level.FINE, "couldn't create listener");
            listener = this.listener;
        } catch (IllegalAccessException e) {
            Logger.getLogger(ArtifactoryServer.class.getName()).log(Level.FINE, "couldn't create listener");
            listener = this.listener;
        }
        return listener;
    }

    public String getServerName() {
        return serverName;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setBypassProxy(boolean bypassProxy) {
        this.bypassProxy = bypassProxy;
    }

    public boolean isBypassProxy() {
        return bypassProxy;
    }

}
