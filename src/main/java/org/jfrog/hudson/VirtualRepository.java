package org.jfrog.hudson;

/**
 * Holder object that represents virtual repositories with their display name and their actual value
 *
 * @author Tomer Cohen
 */
public class VirtualRepository extends Repository {

    private final String displayName;

    public VirtualRepository(String displayName, String value) {
        super(value);
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

}
