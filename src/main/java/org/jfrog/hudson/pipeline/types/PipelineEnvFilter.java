package org.jfrog.hudson.pipeline.types;

import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.jfrog.build.extractor.clientConfiguration.IncludeExcludePatterns;

import java.io.Serializable;

/**
 * Created by romang on 6/22/16.
 */
public class PipelineEnvFilter implements Serializable {

    private IncludeExcludePatterns patternFilter;
    private final String DEFAULT_EXCLUDE_PATTERN = "*password*,*secret*,*key*";

    public PipelineEnvFilter() {
        this.patternFilter = new IncludeExcludePatterns("", DEFAULT_EXCLUDE_PATTERN);
    }

    @Whitelisted
    public PipelineEnvFilter addInclude(String includePattern) {
        patternFilter.addIncludePatterns(includePattern);
        return this;
    }

    @Whitelisted
    public PipelineEnvFilter addExclude(String excludePattern) {
        patternFilter.addExcludePatterns(excludePattern);
        return this;
    }

    @Whitelisted
    public PipelineEnvFilter clear() {
        patternFilter = new IncludeExcludePatterns("", "");
        return this;
    }

    @Whitelisted
    public PipelineEnvFilter reset() {
        patternFilter = new IncludeExcludePatterns("", DEFAULT_EXCLUDE_PATTERN);
        return this;
    }

    protected IncludeExcludePatterns getPatternFilter() {
        return patternFilter;
    }
}
