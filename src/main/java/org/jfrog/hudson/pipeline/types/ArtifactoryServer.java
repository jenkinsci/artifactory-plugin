package org.jfrog.hudson.pipeline.types;

import com.google.common.collect.Maps;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.jenkinsci.plugins.workflow.cps.CpsScript;
import org.jfrog.hudson.CredentialsConfig;
import org.jfrog.hudson.pipeline.Utils;
import org.jfrog.hudson.pipeline.types.buildInfo.BuildInfo;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.jfrog.hudson.pipeline.Utils.BUILD_INFO;
import static org.jfrog.hudson.pipeline.Utils.appendBuildInfo;

/**
 * Created by romang on 4/21/16.
 */
public class ArtifactoryServer implements Serializable {
    public static final String SPEC = "spec";
    public static final String SERVER = "server";
    public static final String BUILD_NAME = "buildName";
    public static final String BUILD_NUMBER = "buildNumber";

    private String serverName;
    private String url;
    private String username;
    private String password;
    private String credentialsId;
    private boolean bypassProxy;
    private transient CpsScript cpsScript;
    private boolean usesCredentialsId;
    private Connection connection = new Connection();
    private int fileSpecThreadNumber;

    public ArtifactoryServer() {
    }

    public ArtifactoryServer(String artifactoryServerName, String url, String username, String password, int fileSpecThreadNumber) {
        serverName = artifactoryServerName;
        this.url = url;
        this.username = username;
        this.password = password;
        this.fileSpecThreadNumber = fileSpecThreadNumber;
    }

    public ArtifactoryServer(String url, String username, String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    public ArtifactoryServer(String url, String credentialsId) {
        this.url = url;
        this.credentialsId = credentialsId;
        this.usesCredentialsId = true;
    }

    public CredentialsConfig createCredentialsConfig() {
        CredentialsConfig credentialsConfig = new CredentialsConfig(this.username, this.password, this.credentialsId, null);
        credentialsConfig.setIgnoreCredentialPluginDisabled(usesCredentialsId);
        return credentialsConfig;
    }

    public void setCpsScript(CpsScript cpsScript) {
        this.cpsScript = cpsScript;
    }

    @Whitelisted
    public void download(String spec) {
        download(spec, null);
    }

    private Map<String, Object> getDownloadUploadObjectMap(Map<String, Object> arguments) {
        if (!arguments.containsKey(SPEC)) {
            throw new IllegalArgumentException(SPEC + " is a mandatory arguments");
        }

        List<String> keysAsList = Arrays.asList(SPEC, BUILD_INFO);
        if (!keysAsList.containsAll(arguments.keySet())) {
            throw new IllegalArgumentException("Only the following arguments are allowed, " + keysAsList.toString());
        }

        Map<String, Object> stepVariables = Maps.newLinkedHashMap(arguments);
        stepVariables.put(SERVER, this);
        return stepVariables;
    }

    @Whitelisted
    public void download(String spec, BuildInfo providedBuildInfo) {
        Map<String, Object> stepVariables = Maps.newLinkedHashMap();
        stepVariables.put(SPEC, spec);
        stepVariables.put(BUILD_INFO, providedBuildInfo);
        stepVariables.put(SERVER, this);
        appendBuildInfo(cpsScript, stepVariables);

        // Throws CpsCallableInvocation - Must be the last line in this method
        cpsScript.invokeMethod("artifactoryDownload", stepVariables);
    }

    @Whitelisted
    public void download(Map<String, Object> downloadArguments) {
        Map<String, Object> stepVariables = getDownloadUploadObjectMap(downloadArguments);
        appendBuildInfo(cpsScript, stepVariables);

        // Throws CpsCallableInvocation - Must be the last line in this method
        cpsScript.invokeMethod("artifactoryDownload", stepVariables);
    }

    @Whitelisted
    public void upload(String spec) {
        upload(spec, null);
    }

    @Whitelisted
    public void upload(Map<String, Object> uploadArguments) {
        Map<String, Object> stepVariables = getDownloadUploadObjectMap(uploadArguments);
        appendBuildInfo(cpsScript, stepVariables);

        // Throws CpsCallableInvocation - Must be the last line in this method
        cpsScript.invokeMethod("artifactoryUpload", stepVariables);
    }

    @Whitelisted
    public void upload(String spec, BuildInfo buildInfo) {
        Map<String, Object> stepVariables = Maps.newLinkedHashMap();
        stepVariables.put(SPEC, spec);
        stepVariables.put(BUILD_INFO, buildInfo);
        stepVariables.put(SERVER, this);
        appendBuildInfo(cpsScript, stepVariables);

        // Throws CpsCallableInvocation - Must be the last line in this method
        cpsScript.invokeMethod("artifactoryUpload", stepVariables);
    }

    @Whitelisted
    public void publishBuildInfo(BuildInfo buildInfo) {
        Map<String, Object> stepVariables = Maps.newLinkedHashMap();
        stepVariables.put(BUILD_INFO, buildInfo);
        stepVariables.put(SERVER, this);

        // Throws CpsCallableInvocation - Must be the last line in this method
        cpsScript.invokeMethod("publishBuildInfo", stepVariables);
    }

    @Whitelisted
    public void promote(Map<String, Object> promotionParams) {
        Map<String, Object> stepVariables = Maps.newLinkedHashMap();
        stepVariables.put("promotionConfig", Utils.createPromotionConfig(promotionParams, true));
        stepVariables.put(SERVER, this);

        // Throws CpsCallableInvocation - Must be the last line in this method
        cpsScript.invokeMethod("artifactoryPromoteBuild", stepVariables);
    }

    @Whitelisted
    public void distribute(Map<String, Object> distributionParams) {
        Map<String, Object> stepVariables = Maps.newLinkedHashMap();
        stepVariables.put("distributionConfig", Utils.createDistributionConfig(distributionParams));
        stepVariables.put(SERVER, this);

        // Throws CpsCallableInvocation - Must be the last line in this method
        cpsScript.invokeMethod("artifactoryDistributeBuild", stepVariables);
    }

    @Whitelisted
    public void xrayScan(Map<String, Object> xrayScanParams) {
        Map<String, Object> stepVariables = Maps.newLinkedHashMap();
        stepVariables.put("xrayScanConfig", createXrayScanConfig(xrayScanParams));
        stepVariables.put(SERVER, this);

        // Throws CpsCallableInvocation - Must be the last line in this method
        cpsScript.invokeMethod("xrayScanBuild", stepVariables);
    }

    private XrayScanConfig createXrayScanConfig(Map<String, Object> xrayScanParams) {
        final String failBuild = "failBuild";

        List<String> mandatoryArgumentsAsList = Arrays.asList(BUILD_NAME, BUILD_NAME);
        if (!xrayScanParams.keySet().containsAll(mandatoryArgumentsAsList)) {
            throw new IllegalArgumentException(mandatoryArgumentsAsList.toString() + " are mandatory arguments");
        }

        Set<String> xrayScanParamsSet = xrayScanParams.keySet();
        List<String> keysAsList = Arrays.asList(BUILD_NAME, BUILD_NUMBER, failBuild);
        if (!keysAsList.containsAll(xrayScanParamsSet)) {
            throw new IllegalArgumentException("Only the following arguments are allowed: " + keysAsList.toString());
        }

        return new XrayScanConfig((String) xrayScanParams.get(BUILD_NAME),
                (String) xrayScanParams.get(BUILD_NUMBER), (Boolean) xrayScanParams.get(failBuild));
    }

    public String getServerName() {
        return serverName;
    }

    @Whitelisted
    public String getUrl() {
        return url;
    }

    @Whitelisted
    public void setUrl(String url) {
        this.url = url;
    }

    @Whitelisted
    public String getUsername() {
        return username;
    }

    @Whitelisted
    public void setUsername(String username) {
        this.username = username;
        this.credentialsId = "";
        this.usesCredentialsId = false;
    }

    @Whitelisted
    public void setPassword(String password) {
        this.password = password;
        this.credentialsId = "";
        this.usesCredentialsId = false;
    }

    public String getPassword() {
        return this.password;
    }

    @Whitelisted
    public void setBypassProxy(boolean bypassProxy) {
        this.bypassProxy = bypassProxy;
    }

    @Whitelisted
    public boolean isBypassProxy() {
        return bypassProxy;
    }

    @Whitelisted
    public String getCredentialsId() {
        return credentialsId;
    }

    @Whitelisted
    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
        this.password = "";
        this.username = "";
        this.usesCredentialsId = true;
    }

    @Whitelisted
    public Connection getConnection() {
        return connection;
    }

    @Whitelisted
    public int getFileSpecThreads() {
        return fileSpecThreadNumber;
    }

}
