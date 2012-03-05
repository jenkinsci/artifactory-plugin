package org.jfrog.hudson;

/**
 * @author Noam Y. Tenne
 */
public class UserPluginInfoParam {

    private Object key;
    private Object defaultValue;

    public UserPluginInfoParam(Object key, Object defaultValue) {
        this.key = key;
        this.defaultValue = defaultValue;
    }

    public Object getKey() {
        return key;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }
}
