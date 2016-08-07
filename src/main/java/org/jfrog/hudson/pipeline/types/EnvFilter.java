package org.jfrog.hudson.pipeline.types;

import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.jfrog.build.extractor.clientConfiguration.IncludeExcludePatterns;

import java.io.Serializable;

/**
 * Created by romang on 6/22/16.
 */
public class EnvFilter implements Serializable {

    private IncludeExcludePatterns patternFilter;
    private final String DEFAULT_EXCLUDE_PATTERN = "*password*,*secret*,*key*";

    public EnvFilter() {
        this.patternFilter = new IncludeExcludePatterns("", DEFAULT_EXCLUDE_PATTERN);
    }

    @Whitelisted
    public EnvFilter addInclude(String includePattern) {
        patternFilter.addIncludePatterns(includePattern);
        return this;
    }

    @Whitelisted
    public EnvFilter addExclude(String excludePattern) {
        patternFilter.addExcludePatterns(excludePattern);
        return this;
    }

    @Whitelisted
    public EnvFilter clear() {
        patternFilter = new IncludeExcludePatterns("", "");
        return this;
    }

    @Whitelisted
    public EnvFilter reset() {
        patternFilter = new IncludeExcludePatterns("", DEFAULT_EXCLUDE_PATTERN);
        return this;
    }

    protected IncludeExcludePatterns getPatternFilter() {
        return patternFilter;
    }
}
