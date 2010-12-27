package org.jfrog.hudson;

/**
 * Holder object that represents virtual repositories with their display name and their actual value
 *
 * @author Tomer Cohen
 */
public class VirtualRepository {

    private final String displayName;
    private final String value;

    public VirtualRepository(String displayName, String value) {
        this.displayName = displayName;
        this.value = value;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getValue() {
        return value;
    }
}
