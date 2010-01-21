package org.jfrog.hudson;

import org.artifactory.build.api.Agent;
import org.artifactory.build.api.Build;
import org.artifactory.build.api.builder.BuildInfoBuilder;
import org.testng.annotations.Test;

import java.io.IOException;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * @author Yossi Shaul
 */
@Test
public class BuildInfoDeployerTest {

    public void simplestBuildInfoToJSON() throws IOException {
        Build buildInfo = new BuildInfoBuilder().build();
        buildInfo.setAgent(new Agent("Hudson", "1.888"));
        BuildInfoDeployer buildInfoDeployer = new BuildInfoDeployer(null, null, null);
        String buildInfoJson = buildInfoDeployer.buildInfoToJsonString(buildInfo);
        assertNotNull(buildInfoJson, "Got null json result");
        assertTrue(buildInfoJson.contains("\"agent\":{\"name\":\"Hudson\",\"version\":\"1.888\""),
                "Unexpected json result:" + buildInfoJson);
    }
}
