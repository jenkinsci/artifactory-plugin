package org.jfrog.hudson.pipeline.types;

import hudson.EnvVars;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.jenkinsci.plugins.workflow.cps.CpsScript;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jfrog.build.extractor.clientConfiguration.IncludeExcludePatterns;
import org.jfrog.build.extractor.clientConfiguration.PatternMatcher;

import java.io.Serializable;
import java.util.*;

/**
 * Created by romang on 6/22/16.
 */
public class PipelineEnv implements Serializable {
    private Map<String, String> envVars = new HashMap<String, String>();
    private Map<String, String> sysVars = new HashMap<String, String>();
    private PipelineEnvFilter filter = new PipelineEnvFilter();
    private boolean capture = false; //By default don't collect

    private CpsScript cpsScript;

    public PipelineEnv() {
    }


    /**
     * Collect environment variables and system properties under with filter constrains
     *
     * @param context
     * @throws Exception
     */
    public void collectVariables(StepContext context) throws Exception {

        EnvVars env = context.get(EnvVars.class);
        addAllWithFilter(envVars, env, filter.getPatternFilter());

        Map<String, String> sysEnv = new HashMap<String, String>();
        Properties systemProperties = System.getProperties();
        Enumeration<?> enumeration = systemProperties.propertyNames();
        while (enumeration.hasMoreElements()) {
            String propertyKey = (String) enumeration.nextElement();
            sysEnv.put(propertyKey, systemProperties.getProperty(propertyKey));
        }
        addAllWithFilter(sysVars, sysEnv, filter.getPatternFilter());
    }

    /**
     * Append environment variables and system properties from othre PipelineEvn object
     *
     * @param env
     */
    protected void append(PipelineEnv env) {
        addAllWithFilter(this.envVars, env.envVars, filter.getPatternFilter());
        addAllWithFilter(this.envVars, env.sysVars, filter.getPatternFilter());
    }

    /**
     * Adds all pairs from 'fromMap' to 'toMap' excluding once that matching the pattern
     *
     * @param toMap
     * @param fromMap
     * @param pattern
     */
    private void addAllWithFilter(Map<String, String> toMap, Map<String, String> fromMap, IncludeExcludePatterns pattern) {
        Iterator entries = fromMap.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry entry = (Map.Entry) entries.next();
            String key = (String) entry.getKey();
            if (PatternMatcher.pathConflicts(key, pattern)) {
                continue;
            }
            toMap.put(key, (String) entry.getValue());
        }
    }

    @Whitelisted
    public Map<String, String> getVars() {
        Map<String, String> vars = new HashMap<String, String>();
        vars.putAll(envVars);
        vars.putAll(sysVars);
        return vars;
    }

    @Whitelisted
    public void setCapture(boolean capture) {
        this.capture = capture;
    }

    @Whitelisted
    public boolean isCapture() {
        return capture;
    }

    @Whitelisted
    public void collect() {
        Map<String, Object> stepVariables = new LinkedHashMap<String, Object>();
        stepVariables.put("env", this);
        cpsScript.invokeMethod("collectEnv", stepVariables);
    }

    @Whitelisted
    public PipelineEnvFilter getFilter() {
        return filter;
    }

    protected Map<String, String> getEnvVars() {
        return envVars;
    }

    protected Map<String, String> getSysVars() {
        return sysVars;
    }

    public void setCpsScript(CpsScript cpsScript) {
        this.cpsScript = cpsScript;
    }
}
