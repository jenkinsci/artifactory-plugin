package org.jfrog.hudson;

import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.text.ParseException;

import static org.jfrog.build.extractor.clientConfiguration.client.artifactory.services.PublishBuildInfo.createBuildInfoUrl;

/**
 * Created by yahavi on 28/03/2017.
 */
public class PublishedBuildDetails implements Serializable {
    private static final long serialVersionUID = 1L;

    private String artifactoryUrl;
    private String buildName;
    private String buildNumber;
    private String platformUrl;
    private String startedTimeStamp;
    private String project;

    public PublishedBuildDetails(String artifactoryUrl, String buildName, String buildNumber) {
        this.artifactoryUrl = artifactoryUrl;
        this.buildName = buildName;
        this.buildNumber = buildNumber;
    }

    public PublishedBuildDetails(String artifactoryUrl, String buildName, String buildNumber, String platformUrl, String startedTimeStamp, String project) {
        this(artifactoryUrl, buildName, buildNumber);
        this.platformUrl = platformUrl;
        this.startedTimeStamp = startedTimeStamp;
        this.project = project;
    }

    public String getBuildInfoUrl() throws ParseException {
        if (StringUtils.isNotBlank(platformUrl) && StringUtils.isNotBlank(startedTimeStamp)) {
            // Encode already happened in BuildInfoResultAction#createBuildInfoIdentifier.
            return createBuildInfoUrl(platformUrl, buildName, buildNumber, startedTimeStamp, project, false);
        }
        return createBuildInfoUrl(this.artifactoryUrl, this.buildName, this.buildNumber, false);
    }

    public String getDisplayName() {
        return this.buildName + " / " + this.buildNumber;
    }
}