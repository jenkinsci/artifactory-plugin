package org.jfrog.hudson;

/**
 * @author Noam Y. Tenne
 */
public class UserPluginInfoParam {

    private String key;
    private String displayName;
    private String defaultValue;

    public UserPluginInfoParam(String key, String displayName, String defaultValue) {
        this.key = key;
        this.displayName = displayName;
        this.defaultValue = defaultValue;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDefaultValue() {
        return defaultValue;
    }
}
