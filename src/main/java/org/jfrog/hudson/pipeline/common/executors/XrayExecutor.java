package org.jfrog.hudson.pipeline.common.executors;

import hudson.Util;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.apache.commons.lang3.StringUtils;
import org.jfrog.build.api.util.Log;
import org.jfrog.build.client.ProxyConfiguration;
import org.jfrog.build.client.artifactoryXrayResponse.ArtifactoryXrayResponse;
import org.jfrog.build.extractor.buildScanTable.BuildScanTableHelper;
import org.jfrog.build.extractor.clientConfiguration.client.artifactory.ArtifactoryManager;
import org.jfrog.hudson.XrayScanResultAction;
import org.jfrog.hudson.pipeline.common.Utils;
import org.jfrog.hudson.pipeline.common.types.ArtifactoryServer;
import org.jfrog.hudson.pipeline.common.types.XrayScanConfig;
import org.jfrog.hudson.pipeline.common.types.XrayScanResult;
import org.jfrog.hudson.util.Credentials;
import org.jfrog.hudson.util.JenkinsBuildInfoLog;

/**
 * @author Alexei Vainshtein
 */
public class XrayExecutor implements Executor {

    private final XrayScanConfig xrayScanConfig;
    private final ArtifactoryServer server;
    private final TaskListener listener;
    private final Run<?, ?> build;

    private XrayScanResult xrayScanResult;

    public XrayExecutor(XrayScanConfig xrayScanConfig, TaskListener listener, ArtifactoryServer server, Run<?, ?> build) {
        this.xrayScanConfig = xrayScanConfig;
        this.listener = listener;
        this.server = server;
        this.build = build;
    }

    @Override
    public void execute() throws Exception {
        Log log = new JenkinsBuildInfoLog(listener);
        Credentials credentials = server.createCredentialsConfig().provideCredentials(build.getParent());
        ArtifactoryManager artifactoryManager = new ArtifactoryManager(server.getUrl(), credentials.getUsername(),
                credentials.getPassword(), credentials.getAccessToken(), log);
        ProxyConfiguration proxyConfiguration = Utils.getProxyConfiguration(Utils.prepareArtifactoryServer(null, server));
        if (proxyConfiguration != null) {
            artifactoryManager.setProxyConfiguration(proxyConfiguration);
        }
        ArtifactoryXrayResponse buildScanResult = artifactoryManager.scanBuild(xrayScanConfig.getBuildName(), xrayScanConfig.getBuildNumber(), xrayScanConfig.getProject(), "jenkins");
        xrayScanResult = new XrayScanResult(buildScanResult);

        if (xrayScanResult.isFoundVulnerable()) {
            addXrayResultAction(xrayScanResult.getScanUrl(), xrayScanConfig.getBuildName(), xrayScanConfig.getBuildNumber());

            if (xrayScanConfig.getPrintTable()) {
                new BuildScanTableHelper().printTable(buildScanResult, log);
            }

            if (xrayScanConfig.getFailBuild()) {
                throw new XrayScanException(xrayScanResult);
            }
            log.error(xrayScanResult.getScanMessage());
        } else {
            log.info(xrayScanResult.getScanMessage());
        }

        if (StringUtils.isNotEmpty(xrayScanResult.getScanUrl())) {
            log.info("Xray scan details are available at: " + xrayScanResult.getScanUrl());
        }
    }

    public XrayScanResult getXrayScanResult() {
        return xrayScanResult;
    }

    private void addXrayResultAction(String xrayUrl, String buildName, String buildNumber) {
        String encodedXrayUrl = encodeXrayUrl(xrayUrl, buildName, buildNumber);
        synchronized (build) {
            XrayScanResultAction action = new XrayScanResultAction(encodedXrayUrl, build);
            build.addAction(action);
        }
    }

    /**
     * Encode the buildName and buildNumber of the scan results URL.
     *
     * @param xrayUrl     - The Xray scan results URL
     * @param buildName   - The build name
     * @param buildNumber - The build number
     * @return encoded Xray scan results URL
     */
    private String encodeXrayUrl(String xrayUrl, String buildName, String buildNumber) {
        return StringUtils.replaceOnce(xrayUrl, buildName + "/" + buildNumber, Util.rawEncode(buildName) + "/" + Util.rawEncode(buildNumber));
    }

    public static class XrayScanException extends Exception {

        XrayScanException(XrayScanResult xrayScanResult) {
            super("Violations were found by Xray: " + xrayScanResult, null, true, false);
        }

        @Override
        public String toString() {
            return getLocalizedMessage();
        }
    }
}
