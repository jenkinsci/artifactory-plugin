package org.jfrog.hudson;

/**
 * @author Noam Y. Tenne
 */
public class UserPluginInfoParam {

    private String key;
    private String defaultValue;

    public UserPluginInfoParam(String key, String defaultValue) {
        this.key = key;
        this.defaultValue = defaultValue;
    }

    public String getKey() {
        return key;
    }

    public String getDefaultValue() {
        return defaultValue;
    }
}
