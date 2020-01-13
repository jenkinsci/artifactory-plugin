package org.jfrog.hudson.pipeline.integration;

import org.junit.Test;

/**
 * @author yahavi
 */
public class ScriptedITest extends CommonITestsPipeline {

    public ScriptedITest() {
        super(PipelineType.SCRIPTED);
    }

    @Test
    public void downloadByPatternTest() throws Exception {
        super.downloadByPatternTest("scripted:downloadByPattern test");
    }

    @Test
    public void downloadByAqlTest() throws Exception {
        super.downloadByAqlTest("scripted:downloadByAql test");
    }

    @Test
    public void downloadByPatternAndBuildTest() throws Exception {
        super.downloadByPatternAndBuildTest("scripted:downloadByPatternAndBuild test");
    }

    @Test
    public void downloadByBuildOnlyTest() throws Exception {
        super.downloadByBuildOnlyTest("scripted:downloadByBuildOnly test");
    }

    @Test
    public void downloadNonExistingBuildTest() throws Exception {
        super.downloadNonExistingBuildTest("scripted:downloadNonExistingBuild test");
    }

    @Test
    public void downloadByShaAndBuildTest() throws Exception {
        super.downloadByShaAndBuildTest("scripted:downloadByShaAndBuild test");
    }

    @Test
    public void downloadByShaAndBuildNameTest() throws Exception {
        super.downloadByShaAndBuildNameTest("scripted:downloadByShaAndBuildName test");
    }

    @Test
    public void uploadTest() throws Exception {
        super.uploadTest("scripted:upload test");
    }

    @Test
    public void promotionTest() throws Exception {
        super.promotionTest("scripted:promotion test");
    }

    @Test
    public void mavenTest() throws Exception {
        super.mavenTest("scripted:maven test");
    }

    @Test
    public void gradleTest() throws Exception {
        super.gradleTest("scripted:gradle test");
    }

    @Test
    public void gradleCiServerTest() throws Exception {
        super.gradleCiServerTest("scripted:gradle-ci test");
    }

    @Test
    public void npmTest() throws Exception {
        super.npmTest("scripted:npm test");
    }

    @Test
    public void goTest() throws Exception {
        super.goTest("scripted:go test");
    }

    @Test
    public void setPropsTest() throws Exception {
        super.setPropsTest("scripted:setProps test");
    }

    @Test
    public void deletePropsTest() throws Exception {
        super.deletePropsTest("scripted:deleteProps test");
    }

    @Test
    public void dockerPushTest() throws Exception {
        super.dockerPushTest("scripted:dockerPush test");
    }

    @Test
    public void xrayScanFailTrueTest() throws Exception {
        super.xrayScanTest("scripted:xrayScanFailBuildTrue test", true);
    }

    @Test
    public void xrayScanFailFalseTest() throws Exception {
        super.xrayScanTest("scripted:xrayScanFailBuildFalse test", false);
    }

    @Test
    public void collectIssuesTest() throws Exception {
        super.collectIssuesTest("scripted:collectIssues test");
    }

    @Test
    public void appendBuildInfoTest() throws Exception {
        super.appendBuildInfoTest("scripted:appendBuildInfo test");
    }
}
