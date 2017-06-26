package org.jfrog.hudson.pipeline.types;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.jenkinsci.plugins.workflow.cps.CpsScript;
import org.jfrog.hudson.pipeline.types.buildInfo.BuildInfo;

import java.io.File;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

public class ConanClient implements Serializable {
    public final static String CONAN_LOG_FILE = "conan_log.log";
    private transient CpsScript cpsScript;
    private String userPath;
    private ConanRemote remote = new ConanRemote();

    public ConanClient() {
    }

    public void setCpsScript(CpsScript cpsScript) {
        this.cpsScript = cpsScript;
        this.remote.setCpsScript(cpsScript);
    }

    @Whitelisted
    public String getUserPath() {
        return userPath;
    }

    public void setUserPath(String conanHomePath) {
        this.userPath = conanHomePath;
        this.remote.setConanHome(conanHomePath);
    }

    public String getLogFilePath() {
        if (StringUtils.endsWith(getUserPath(), File.separator)) {
            return getUserPath() + CONAN_LOG_FILE;
        }
        return getUserPath() + File.separator + CONAN_LOG_FILE;
    }

    @Whitelisted
    public ConanRemote getRemote() {
        return remote;
    }

    public void setRemote(ConanRemote remote) {
        this.remote = remote;
    }

    @Whitelisted
    public void run(Map<String, Object> args) {
        if (!args.containsKey("command")) {
            throw new IllegalArgumentException("'command' is a mandatory argument.");
        }
        String command = (String) args.get("command");
        BuildInfo buildInfo = (BuildInfo) args.get("buildInfo");
        cpsScript.invokeMethod("RunConanCommand", getRunCommandExecutionArguments(command, buildInfo));
    }

    private Map<String, Object> getRunCommandExecutionArguments(String command, BuildInfo buildInfo) {
        Map<String, Object> stepVariables = new LinkedHashMap<String, Object>();
        stepVariables.put("command", command);
        stepVariables.put("conanHome", getUserPath());
        stepVariables.put("buildLogPath", getLogFilePath());
        stepVariables.put("buildInfo", buildInfo);
        return stepVariables;
    }
}