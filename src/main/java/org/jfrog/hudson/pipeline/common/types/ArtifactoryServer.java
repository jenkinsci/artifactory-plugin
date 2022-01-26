package org.jfrog.hudson.pipeline.common.types;

import hudson.model.Item;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.jenkinsci.plugins.workflow.cps.CpsScript;
import org.jfrog.hudson.CredentialsConfig;
import org.jfrog.hudson.pipeline.common.Utils;
import org.jfrog.hudson.pipeline.common.types.buildInfo.BuildInfo;
import org.jfrog.hudson.util.Credentials;
import org.jfrog.hudson.util.plugins.PluginsUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.jfrog.build.extractor.clientConfiguration.util.EditPropertiesHelper.EditPropertiesActionType;
import static org.jfrog.hudson.pipeline.common.Utils.BUILD_INFO;
import static org.jfrog.hudson.pipeline.common.Utils.appendBuildInfo;

/**
 * Created by romang on 4/21/16.
 * Represents an instance of Artifactory configuration from pipeline script.
 */
public class ArtifactoryServer implements Serializable {
    public static final long serialVersionUID = 1L;

    public static final String SPEC = "spec";
    public static final String PATHS = "paths";
    public static final String SERVER = "server";
    public static final String BUILD_NAME = "buildName";
    public static final String BUILD_NUMBER = "buildNumber";
    public static final String PROJECT = "project";
    public static final String FAIL_NO_OP = "failNoOp";
    public static final String MODULE = "module";
    public static final String PROPERTIES = "props";
    public static final String EDIT_PROPERTIES_TYPE = "editType";

    private String serverName;
    private String url;
    private String platformUrl;
    private String username;
    private String password;
    private String credentialsId;
    private boolean bypassProxy;
    private transient CpsScript cpsScript;
    private boolean usesCredentialsId;
    private final Connection connection = new Connection();
    private int deploymentThreads;

    public ArtifactoryServer() {
    }

    public ArtifactoryServer(String artifactoryServerName, String url, int deploymentThreads) {
        serverName = artifactoryServerName;
        this.url = url;
        this.deploymentThreads = deploymentThreads;

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

    public ArtifactoryServer(org.jfrog.hudson.ArtifactoryServer jenkinsArtifactoryServer, Item parent) {
        serverName = jenkinsArtifactoryServer.getServerId();
        url = jenkinsArtifactoryServer.getArtifactoryUrl();
        this.deploymentThreads = jenkinsArtifactoryServer.getDeploymentThreads();
        if (PluginsUtils.isCredentialsPluginEnabled()) {
            credentialsId = jenkinsArtifactoryServer.getResolvingCredentialsConfig().getCredentialsId();
        } else {
            Credentials serverCredentials = jenkinsArtifactoryServer.getResolvingCredentialsConfig().provideCredentials(parent);
            username = serverCredentials.getUsername();
            password = serverCredentials.getPassword();
        }
        bypassProxy = jenkinsArtifactoryServer.isBypassProxy();
        connection.setRetry(jenkinsArtifactoryServer.getConnectionRetry());
        connection.setTimeout(jenkinsArtifactoryServer.getTimeout());
    }

    public CredentialsConfig createCredentialsConfig() {
        CredentialsConfig credentialsConfig = new CredentialsConfig(this.username, this.password, this.credentialsId, null);
        credentialsConfig.setIgnoreCredentialPluginDisabled(usesCredentialsId);
        return credentialsConfig;
    }

    public void setCpsScript(CpsScript cpsScript) {
        this.cpsScript = cpsScript;
    }

    private Map<String, Object> getDownloadUploadObjectMap(Map<String, Object> arguments) {
        if (!arguments.containsKey(SPEC)) {
            throw new IllegalArgumentException(SPEC + " is a mandatory argument");
        }

        List<String> keysAsList = Arrays.asList(SPEC, BUILD_INFO, FAIL_NO_OP, MODULE);
        if (!keysAsList.containsAll(arguments.keySet())) {
            throw new IllegalArgumentException("Only the following arguments are allowed, " + keysAsList.toString());
        }

        Map<String, Object> stepVariables = new LinkedHashMap<>(arguments);
        stepVariables.put(SERVER, this);
        return stepVariables;
    }

    @Whitelisted
    public void download(Map<String, Object> downloadArguments) {
        Map<String, Object> stepVariables = getDownloadUploadObjectMap(downloadArguments);
        appendBuildInfo(cpsScript, stepVariables);

        // Throws CpsCallableInvocation - Must be the last line in this method
        cpsScript.invokeMethod("artifactoryDownload", stepVariables);
    }

    @Whitelisted
    public void download(String spec) {
        download(spec, null, false);
    }

    @Whitelisted
    public void download(String spec, BuildInfo buildInfo) {
        download(spec, buildInfo, false);
    }

    @Whitelisted
    public void download(String spec, boolean failNoOp) {
        download(spec, null, failNoOp);
    }

    @Whitelisted
    public void download(String spec, BuildInfo buildInfo, boolean failNoOp) {
        Map<String, Object> downloadArguments = new LinkedHashMap<>();
        downloadArguments.put(SPEC, spec);
        downloadArguments.put(BUILD_INFO, buildInfo);
        downloadArguments.put(FAIL_NO_OP, failNoOp);
        download(downloadArguments);
    }

    @Whitelisted
    public void upload(Map<String, Object> uploadArguments) {
        Map<String, Object> stepVariables = getDownloadUploadObjectMap(uploadArguments);
        appendBuildInfo(cpsScript, stepVariables);

        // Throws CpsCallableInvocation - Must be the last line in this method
        cpsScript.invokeMethod("artifactoryUpload", stepVariables);
    }

    @Whitelisted
    public void upload(String spec) {
        upload(spec, null, false);
    }

    @Whitelisted
    public void upload(String spec, BuildInfo buildInfo) {
        upload(spec, buildInfo, false);
    }

    @Whitelisted
    public void upload(String spec, boolean failNoOp) {
        upload(spec, null, failNoOp);
    }

    @Whitelisted
    public void upload(String spec, BuildInfo buildInfo, boolean failNoOp) {
        Map<String, Object> uploadArguments = new LinkedHashMap<>();
        uploadArguments.put(SPEC, spec);
        uploadArguments.put(BUILD_INFO, buildInfo);
        uploadArguments.put(FAIL_NO_OP, failNoOp);
        upload(uploadArguments);
    }

    private Map<String, Object> getPropsObjectMap(Map<String, Object> arguments) {
        if (!arguments.containsKey(SPEC) || !arguments.containsKey(PROPERTIES)) {
            throw new IllegalArgumentException(SPEC + PROPERTIES + " are mandatory arguments");
        }

        List<String> keysAsList = Arrays.asList(SPEC, PROPERTIES, FAIL_NO_OP);
        if (!keysAsList.containsAll(arguments.keySet())) {
            throw new IllegalArgumentException("Only the following arguments are allowed, " + keysAsList.toString());
        }

        Map<String, Object> stepVariables = new LinkedHashMap<>(arguments);
        stepVariables.put(SERVER, this);
        return stepVariables;
    }

    @Whitelisted
    public void setProps(Map<String, Object> propsArguments) {
        Map<String, Object> stepVariables = getPropsObjectMap(propsArguments);
        stepVariables.put(EDIT_PROPERTIES_TYPE, EditPropertiesActionType.SET);

        // Throws CpsCallableInvocation - Must be the last line in this method
        cpsScript.invokeMethod("artifactoryEditProps", stepVariables);
    }

    @Whitelisted
    public void setProps(String spec, String props) {
        setProps(spec, props, false);
    }

    @Whitelisted
    public void setProps(String spec, String props, boolean failNoOp) {
        Map<String, Object> propsArguments = new LinkedHashMap<>();
        propsArguments.put(SPEC, spec);
        propsArguments.put(PROPERTIES, props);
        propsArguments.put(FAIL_NO_OP, failNoOp);
        setProps(propsArguments);
    }

    @Whitelisted
    public void deleteProps(Map<String, Object> propsArguments) {
        Map<String, Object> stepVariables = getPropsObjectMap(propsArguments);
        stepVariables.put(EDIT_PROPERTIES_TYPE, EditPropertiesActionType.DELETE);

        // Throws CpsCallableInvocation - Must be the last line in this method
        cpsScript.invokeMethod("artifactoryEditProps", stepVariables);
    }

    @Whitelisted
    public void deleteProps(String spec, String props) {
        deleteProps(spec, props, false);
    }

    @Whitelisted
    public void deleteProps(String spec, String props, boolean failNoOp) {
        Map<String, Object> propsArguments = new LinkedHashMap<>();
        propsArguments.put(SPEC, spec);
        propsArguments.put(PROPERTIES, props);
        propsArguments.put(FAIL_NO_OP, failNoOp);
        deleteProps(propsArguments);
    }


    @Whitelisted
    public void publishBuildInfo(BuildInfo buildInfo) {
        Map<String, Object> stepVariables = new LinkedHashMap<>();
        stepVariables.put(BUILD_INFO, buildInfo);
        stepVariables.put(SERVER, this);

        // Throws CpsCallableInvocation - Must be the last line in this method
        cpsScript.invokeMethod("publishBuildInfo", stepVariables);
    }

    @Whitelisted
    public void buildAppend(BuildInfo buildInfo, String buildName, String buildNumber) {
        Map<String, Object> stepVariables = new LinkedHashMap<>();
        stepVariables.put(BUILD_INFO, buildInfo);
        stepVariables.put(BUILD_NAME, buildName);
        stepVariables.put(BUILD_NUMBER, buildNumber);
        stepVariables.put(SERVER, this);

        // Throws CpsCallableInvocation - Must be the last line in this method
        cpsScript.invokeMethod("buildAppend", stepVariables);
    }

    @Whitelisted
    public void promote(Map<String, Object> promotionParams) {
        Map<String, Object> stepVariables = new LinkedHashMap<>();
        stepVariables.put("promotionConfig", Utils.createPromotionConfig(promotionParams, true));
        stepVariables.put(SERVER, this);

        // Throws CpsCallableInvocation - Must be the last line in this method
        cpsScript.invokeMethod("artifactoryPromoteBuild", stepVariables);
    }

    @Whitelisted
    public void distribute(Map<String, Object> distributionParams) {
        Map<String, Object> stepVariables = new LinkedHashMap<>();
        stepVariables.put("distributionConfig", Utils.createDistributionConfig(distributionParams));
        stepVariables.put(SERVER, this);

        // Throws CpsCallableInvocation - Must be the last line in this method
        cpsScript.invokeMethod("artifactoryDistributeBuild", stepVariables);
    }

    @Whitelisted
    public void xrayScan(Map<String, Object> xrayScanParams) {
        Map<String, Object> stepVariables = new LinkedHashMap<>();
        stepVariables.put("xrayScanConfig", Utils.createXrayScanConfig(xrayScanParams));
        stepVariables.put(SERVER, this);

        // Throws CpsCallableInvocation - Must be the last line in this method
        cpsScript.invokeMethod("xrayScanBuild", stepVariables);
    }

    @Whitelisted
    public void setBuildTrigger(Map<String, Object> arguments) {
        List<String> mandatoryParams = new ArrayList<>(Arrays.asList(PATHS, SPEC));
        if (!arguments.keySet().containsAll(mandatoryParams)) {
            throw new IllegalArgumentException(mandatoryParams.toString() + " are mandatory arguments");
        }
        Map<String, Object> stepVariables = new LinkedHashMap<>();
        stepVariables.put(SERVER, this);
        stepVariables.put(PATHS, arguments.get(PATHS));
        stepVariables.put(SPEC, arguments.get(SPEC));

        // Throws CpsCallableInvocation - Must be the last line in this method
        cpsScript.invokeMethod("artifactoryBuildTrigger", stepVariables);
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
    public int getDeploymentThreads() {
        return deploymentThreads;
    }

    @Whitelisted
    public void setDeploymentThreads(int deploymentThreads) {
        this.deploymentThreads = deploymentThreads;
    }

    public String getPlatformUrl() {
        return platformUrl;
    }

    public void setPlatformUrl(String platformUrl) {
        this.platformUrl = platformUrl;
    }
}
