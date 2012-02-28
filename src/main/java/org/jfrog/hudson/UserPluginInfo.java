package org.jfrog.hudson;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.util.List;
import java.util.Map;

/**
 * @author Noam Y. Tenne
 */
public class UserPluginInfo {

    private String pluginName;
    private Map<String, String> pluginParams;

    public UserPluginInfo(Map stagingPluginInfo) {
        pluginName = stagingPluginInfo.get("name").toString();
        Object params = stagingPluginInfo.get("params");
        if (params == null) {
            pluginParams = Maps.newHashMap();
        } else {
            pluginParams = (Map<String, String>) params;
        }
    }

    public String getPluginName() {
        return pluginName;
    }

    public List<UserPluginInfoParam> getPluginParams() {
        List<UserPluginInfoParam> pluginParamList = Lists.newArrayList();
        for (Map.Entry<String, String> paramEntry : pluginParams.entrySet()) {
            pluginParamList.add(new UserPluginInfoParam(paramEntry.getKey(), paramEntry.getValue()));
        }

        return pluginParamList;
    }
}
