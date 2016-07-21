package org.jfrog.hudson.pipeline.types;

import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;

import java.io.Serializable;

/**
 * Created by romang on 4/21/16.
 */
public class PromotionConfig implements Serializable {

    private String buildName;
    private String buildNumber;
    private String targetRepo;
    private String sourceRepo;
    private String targetStatus;
    private String comment;
    private boolean includeDependencies;
    private boolean useCopy;

    @Whitelisted
    public String getBuildName() {
        return buildName;
    }

    @Whitelisted
    public void setBuildName(String buildName) {
        this.buildName = buildName;
    }

    @Whitelisted
    public String getBuildNumber() {
        return buildNumber;
    }

    @Whitelisted
    public void setBuildNumber(String buildNumber) {
        this.buildNumber = buildNumber;
    }

    @Whitelisted
    public String getTargetRepo() {
        return targetRepo;
    }

    @Whitelisted
    public void setTargetRepo(String targetRepo) {
        this.targetRepo = targetRepo;
    }

    @Whitelisted
    public String getSourceRepo() {
        return sourceRepo;
    }

    @Whitelisted
    public void setSourceRepo(String sourceRepo) {
        this.sourceRepo = sourceRepo;
    }

    @Whitelisted
    public String getTargetStatus() {
        return targetStatus;
    }

    @Whitelisted
    public void setTargetStatus(String targetStatus) {
        this.targetStatus = targetStatus;
    }

    @Whitelisted
    public String getComment() {
        return comment;
    }

    @Whitelisted
    public void setComment(String comment) {
        this.comment = comment;
    }

    @Whitelisted
    public boolean isIncludeDependencies() {
        return includeDependencies;
    }

    @Whitelisted
    public void setIncludeDependencies(boolean includeDependencies) {
        this.includeDependencies = includeDependencies;
    }

    @Whitelisted
    public boolean isUseCopy() {
        return useCopy;
    }

    @Whitelisted
    public void setUseCopy(boolean useCopy) {
        this.useCopy = useCopy;
    }
}
