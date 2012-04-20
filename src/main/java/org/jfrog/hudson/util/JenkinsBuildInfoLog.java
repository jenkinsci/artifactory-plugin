package org.jfrog.hudson.util;

import hudson.model.BuildListener;
import org.jfrog.build.api.util.Log;

/**
 * Wrapper for Jenkins build logger, records log messages from BuildInfo
 *
 * @author Shay Yaakov
 */
public class JenkinsBuildInfoLog implements Log {
    private BuildListener listener;

    public JenkinsBuildInfoLog(BuildListener listener) {
        this.listener = listener;
    }

    public void debug(String message) {
        listener.getLogger().println(message);
    }

    public void info(String message) {
        listener.getLogger().println(message);
    }

    public void warn(String message) {
        listener.getLogger().println(message);
    }

    public void error(String message) {
        listener.getLogger().println(message);
    }

    public void error(String message, Throwable e) {
        listener.getLogger().println(message);
    }
}
