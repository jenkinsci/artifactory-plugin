package org.jfrog.hudson;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.util.List;
import java.util.Map;

/**
 * @author Noam Y. Tenne
 */
public class UserPluginInfo {

    public static final String NO_PLUGIN_KEY = "None";

    private String pluginName;
    private Map<String, String> pluginParams;

    public static final UserPluginInfo NO_PLUGIN = new UserPluginInfo(NO_PLUGIN_KEY);

    public UserPluginInfo(Map stagingPluginInfo) {
        pluginName = stagingPluginInfo.get("name").toString();
        Object params = stagingPluginInfo.get("params");
        if (params == null) {
            pluginParams = Maps.newHashMap();
        } else {
            pluginParams = (Map<String, String>) params;
        }
    }

    private UserPluginInfo(String name) {
        pluginName = name;
        pluginParams = Maps.newHashMap();
    }

    public String getPluginName() {
        return pluginName;
    }

    public List<UserPluginInfoParam> getPluginParams() {
        List<UserPluginInfoParam> pluginParamList = Lists.newArrayList();
        for (Map.Entry<String, String> paramEntry : pluginParams.entrySet()) {
            String paramKey = paramEntry.getKey();
            pluginParamList.add(new UserPluginInfoParam(paramKey, paramEntry.getValue()));
        }

        return pluginParamList;
    }

    public void addParam(String key, String value) {
        pluginParams.put(key, value);
    }
}
