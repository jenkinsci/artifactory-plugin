package org.jfrog.hudson.pipeline.types;

import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.jenkinsci.plugins.workflow.cps.CpsScript;
import org.jfrog.hudson.pipeline.types.buildInfo.BuildInfo;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by romang on 7/28/16.
 */
public class Docker implements Serializable {
    private CpsScript script;
    private String username;
    private String password;
    private String host;

    public Docker() {
    }

    public Docker(CpsScript script, String username, String password, String host) {
        this.script = script;
        this.username = username;
        this.password = password;
        this.host = host;
    }

    public void setCpsScript(CpsScript script) {
        this.script = script;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setHost(String host) {
        this.host = host;
    }

    @Whitelisted
    public BuildInfo push(String imageTag, String targetRepository) throws Exception {
        return push(imageTag, targetRepository, null);
    }

    @Whitelisted
    public BuildInfo push(String imageTag, String targetRepository, BuildInfo providedBuildInfo) throws Exception {
        Map<String, Object> dockerArguments = new LinkedHashMap<String, Object>();
        dockerArguments.put("image", imageTag);
        dockerArguments.put("targetRepo", targetRepository);
        dockerArguments.put("buildInfo", providedBuildInfo);
        return push(dockerArguments);
    }

    @Whitelisted
    public BuildInfo push(Map<String, Object> dockerArguments) throws Exception {
        dockerArguments.put("username", username);
        dockerArguments.put("password", password);
        dockerArguments.put("host", host);

        BuildInfo buildInfo = (BuildInfo) script.invokeMethod("dockerPushStep", dockerArguments);
        buildInfo.setCpsScript(script);
        return buildInfo;
    }

    @Whitelisted
    public BuildInfo pull(String imageTag) throws Exception {
        return pull(imageTag, null);
    }

    @Whitelisted
    public BuildInfo pull(String imageTag, BuildInfo providedBuildInfo) throws Exception {
        Map<String, Object> pullVariables = new LinkedHashMap<String, Object>();
        pullVariables.put("image", imageTag);
        pullVariables.put("buildInfo", providedBuildInfo);

        return pull(pullVariables);
    }

    @Whitelisted
    public BuildInfo pull(Map<String, Object> dockerArguments) throws Exception {
        dockerArguments.put("username", username);
        dockerArguments.put("password", password);
        dockerArguments.put("host", host);

        return (BuildInfo) script.invokeMethod("dockerPullStep", dockerArguments);
    }
}
