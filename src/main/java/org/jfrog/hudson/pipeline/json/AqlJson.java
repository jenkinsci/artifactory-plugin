package org.jfrog.hudson.pipeline.json;

import net.sf.json.JSONObject;
import org.codehaus.jackson.annotate.JsonProperty;

/**
 * Created by romang on 4/26/16.
 */
public class AqlJson {

    private JSONObject find;

    public String getFind() {
        if (find != null) {
            return "items.find(" + find.toString() + ")";
        }
        return null;
    }

    @JsonProperty("items.find")
    public void setFind(JSONObject find) {
        this.find = find;
    }
}
