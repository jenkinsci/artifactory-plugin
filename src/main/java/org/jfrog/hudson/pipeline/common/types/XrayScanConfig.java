package org.jfrog.hudson.pipeline.common.types;

import java.io.Serializable;

/**
 * Created by romang on 4/21/16.
 */
public class XrayScanConfig implements Serializable {

    private String buildName;
    private String buildNumber;
    private Boolean failBuild;

    public XrayScanConfig() {
    }

    public XrayScanConfig(String buildName, String buildNumber, Boolean failBuild) {
        this.buildName = buildName;
        this.buildNumber = buildNumber;
        this.failBuild = failBuild;
    }

    public String getBuildName() {
        return buildName;
    }

    public String getBuildNumber() {
        return buildNumber;
    }

    public boolean getFailBuild() {
        return failBuild == null || failBuild;
    }

    public void setBuildName(String buildName) {
        this.buildName = buildName;
    }

    public void setBuildNumber(String buildNumber) {
        this.buildNumber = buildNumber;
    }

    public void setFailBuild(Boolean failBuild) {
        this.failBuild = failBuild;
    }
}
