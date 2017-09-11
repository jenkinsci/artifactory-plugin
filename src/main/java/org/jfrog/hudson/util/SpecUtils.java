package org.jfrog.hudson.util;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import org.apache.commons.lang.StringUtils;
import org.jfrog.hudson.SpecConfiguration;

import java.io.IOException;
import java.io.PrintStream;

/**
 * Created by diman on 16/10/2016.
 */
public class SpecUtils {

    public static String getSpecStringFromSpecConf(SpecConfiguration specConfiguration, AbstractBuild<?, ?> build, final BuildListener listener)
            throws IOException, InterruptedException {
        EnvVars env =build.getEnvironment(listener);
        if (StringUtils.isNotBlank(specConfiguration.getFilePath())) {
            String filePath = specConfiguration.getFilePath().trim();
            filePath = Util.replaceMacro(filePath, env);
            FilePath workspace= build.getExecutor().getCurrentWorkspace();
            PrintStream logger =listener.getLogger();
            return buildDownloadSpecPath(filePath, workspace, logger).readToString();
        }
        if (StringUtils.isNotBlank(specConfiguration.getSpec())) {
            try {
                Class.forName("org.jenkinsci.plugins.tokenmacro.TokenMacro");
                return org.jenkinsci.plugins.tokenmacro.TokenMacro.expandAll(build, listener, specConfiguration.getSpec().trim());
            } catch (Throwable ex) {
                return Util.replaceMacro(specConfiguration.getSpec().trim(), env);
            }
        }
        return "";
    }

    private static FilePath buildDownloadSpecPath(String providedPath, FilePath workingDir, PrintStream logger)
            throws IOException, InterruptedException {

        FilePath relativeFile = new FilePath(workingDir, providedPath);
        if (relativeFile.exists() && !relativeFile.isDirectory()) {
            logger.println(String.format("Using spec file: %s", relativeFile.getRemote()));
            return relativeFile;
        }

        throw new IOException(String.format("Could not find spec file in the provided path: %s", relativeFile.getRemote()));
    }
}