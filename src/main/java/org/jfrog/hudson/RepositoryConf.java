package org.jfrog.hudson;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author Aviad Shikloshi
 */
public class RepositoryConf {

    private final String keyFromText;
    private final String keyFromSelect;
    private final boolean dynamicMode;
    private String repoName;

    @DataBoundConstructor
    public RepositoryConf(String keyFromText, String keyFromSelect, boolean dynamicMode) {
        this.keyFromText = keyFromText;
        this.keyFromSelect = keyFromSelect;
        this.dynamicMode = dynamicMode;
    }

    public String getRepoKey() {
        String repoKey;
        if (dynamicMode) {
            repoKey = keyFromText;
        } else {
            repoKey = keyFromSelect;
        }
        return repoKey;
    }

    public String getRepoName() {
        if (repoName == null) {
            return getRepoKey();
        }
        return repoName;
    }

    public String getKeyFromText() {
        return keyFromText;
    }

    public String getKeyFromSelect() {
        return keyFromSelect;
    }

    public boolean isDynamicMode() {
        return dynamicMode;
    }
}
