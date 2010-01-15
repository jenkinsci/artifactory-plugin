package org.jfrog.hudson;

import net.sf.json.JSONObject;
import org.artifactory.build.api.Build;
import org.artifactory.build.api.builder.BuildInfoBuilder;
import org.testng.annotations.Test;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

/**
 * @author Yossi Shaul
 */
@Test
public class BuildInfoDeployerTest {

    public void simplestBuildInfoToJSON() {
        Build buildInfo = new BuildInfoBuilder().build();
        BuildInfoDeployer buildInfoDeployer = new BuildInfoDeployer(null, null, null);
        JSONObject buildInfoJson = buildInfoDeployer.buildInfoToJsonObject(buildInfo);
        assertNotNull(buildInfoJson, "Got null json object");
        assertNull(buildInfoJson.get("STARTED_FORMAT"),
                "Build info should not contain this public static final field");
    }
}
