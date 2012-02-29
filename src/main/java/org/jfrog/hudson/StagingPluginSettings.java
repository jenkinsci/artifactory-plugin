package org.jfrog.hudson;

import java.util.Map;

/**
 * @author Noam Y. Tenne
 */
public class StagingPluginSettings {

    private String pluginName;
    private Map<String, String> paramMap;

    public StagingPluginSettings(String pluginName, Map<String, String> paramMap) {
        this.pluginName = pluginName;
        this.paramMap = paramMap;
    }

    public StagingPluginSettings() {
    }

    public String getPluginName() {
        return pluginName;
    }

    public void setPluginName(String pluginName) {
        this.pluginName = pluginName;
    }

    public Map<String, String> getParamMap() {
        return paramMap;
    }

    public void setParamMap(Map<String, String> paramMap) {
        this.paramMap = paramMap;
    }

    @Override
    public String toString() {
        return pluginName;
    }

    public String getPluginParamValue(String paramKey) {
        return (paramMap != null) ? paramMap.get(paramKey) : null;
    }
}
