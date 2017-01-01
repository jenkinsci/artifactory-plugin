package org.jfrog.hudson.pipeline.types;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.RuntimeJsonMappingException;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.jfrog.hudson.pipeline.Utils;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Created by romang on 12/22/16.
 */
public class XrayScanResult implements Serializable {
    private JsonNode scanResult;

    public XrayScanResult(JsonNode result) {
        this.scanResult = result;
    }

    @Whitelisted
    public String toString() {
        try {
            return Utils.mapper().writerWithDefaultPrettyPrinter().writeValueAsString(scanResult);
        } catch (JsonProcessingException e) {
            throw new RuntimeJsonMappingException("Failed while processing the JSON result: \n" + Arrays.toString(e.getStackTrace()));
        }
    }

    public String getScanMassege() {
        return scanResult.get("summary").get("message").asText();
    }

    public String getScanUrl() {
        return scanResult.get("summary").get("more_details_url").asText();
    }

    public boolean isFoundVulnerable() {
        return scanResult.get("summary").get("fail_build").asBoolean();
    }
}