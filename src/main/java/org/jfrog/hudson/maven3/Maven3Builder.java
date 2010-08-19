/*
 * Copyright (C) 2010 JFrog Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jfrog.hudson.maven3;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Hudson;
import hudson.model.JDK;
import hudson.model.Result;
import hudson.model.Run;
import hudson.remoting.Which;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.tasks.Maven;
import hudson.tools.ToolInstallation;
import hudson.util.ArgumentListBuilder;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jfrog.build.api.BuildInfoConfigProperties;
import org.jfrog.build.extractor.maven.Maven3BuildInfoLogger;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;

/**
 * Maven3 builder.
 *
 * @author Yossi Shaul
 */
public class Maven3Builder extends Builder {

    public static final String CLASSWORLDS_LAUNCHER = "org.codehaus.plexus.classworlds.launcher.Launcher";

    private final String mavenName;
    private final String rootPom;
    private final String goals;
    private final String mavenOpts;

    @DataBoundConstructor
    public Maven3Builder(String mavenName, String rootPom, String goals, String mavenOpts) {
        this.mavenName = mavenName;
        this.rootPom = rootPom;
        this.goals = goals;
        this.mavenOpts = mavenOpts;
    }

    public String getMavenName() {
        return mavenName;
    }

    public String getRootPom() {
        return rootPom;
    }

    public String getGoals() {
        return goals;
    }

    public String getMavenOpts() {
        return mavenOpts;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException, IOException {
        EnvVars env = build.getEnvironment(listener);
        FilePath workDir = build.getModuleRoot();
        ArgumentListBuilder cmdLine = buildMavenCmdLine(build, launcher, listener, env);
        StringBuilder javaPathBuilder = new StringBuilder();

        JDK configuredJdk = build.getProject().getJDK();
        if (configuredJdk != null) {
            javaPathBuilder.append(build.getProject().getJDK().getBinDir().getCanonicalPath()).append(File.separator);
        }
        javaPathBuilder.append("java");
        if (!launcher.isUnix()) {
            javaPathBuilder.append(".exe");
        }
        String[] cmds = cmdLine.toCommandArray();
        try {
            //listener.getLogger().println("Executing: " + cmdLine.toStringWithQuote());
            int exitValue =
                    launcher.launch().cmds(new File(javaPathBuilder.toString()), cmds).envs(env).stdout(listener)
                            .pwd(workDir).join();
            boolean success = (exitValue == 0);
            build.setResult(success ? Result.SUCCESS : Result.FAILURE);
            return success;
        } catch (IOException e) {
            Util.displayIOException(e, listener);
            e.printStackTrace(listener.fatalError("command execution failed"));
            build.setResult(Result.FAILURE);
            return false;
        }
    }

    private ArgumentListBuilder buildMavenCmdLine(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener,
            EnvVars env) throws IOException, InterruptedException {
        Maven.MavenInstallation mvn = getMavenInstallation();
        if (mvn == null) {
            listener.error("Maven version is not configured for this project. Can't determine which Maven to run");
            throw new Run.RunnerAbortedException();
        }
        if (mvn.getHome() == null) {
            listener.error("Maven '%s' doesn't have its home set", mvn.getName());
            throw new Run.RunnerAbortedException();
        }

        ArgumentListBuilder args = new ArgumentListBuilder();

        File bootDir = new File(mvn.getHomeDir(), "boot");
        File[] candidates = bootDir.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.startsWith("plexus-classworlds");
            }
        });
        if (candidates == null || candidates.length == 0) {
            listener.error("Couldn't find classworlds jar under " + bootDir.getAbsolutePath());
            throw new Run.RunnerAbortedException();
        }

        File classWorldsJar = candidates[0];

        // classpath
        args.add("-classpath");
        //String cpSeparator = launcher.isUnix() ? ":" : ";";

        args.add(new StringBuilder().append(classWorldsJar.getCanonicalPath()).toString());

        // maven opts
        args.addTokenized(getMavenOpts());

        String buildInfoPropertiesFile = env.get(BuildInfoConfigProperties.PROP_PROPS_FILE);
        boolean artifactoryIntegration = StringUtils.isNotBlank(buildInfoPropertiesFile);
        if (artifactoryIntegration) {
            args.add(new StringBuilder("-D").append(BuildInfoConfigProperties.PROP_PROPS_FILE + "=").append(
                    buildInfoPropertiesFile).toString());
        }

        // maven home
        args.add("-Dmaven.home=" + mvn.getHome());

        File classworldsConf;
        if (artifactoryIntegration) {
            // use the classworlds conf packaged with this plugin and resolve the extractor libs
            File libsDirectory = Which.jarFile(Maven3BuildInfoLogger.class).getParentFile();
            args.add("-Dm3plugin.lib=" + libsDirectory.getAbsolutePath());
            URL resource = getClass().getClassLoader().getResource("org/jfrog/hudson/maven3/classworlds.conf");
            classworldsConf = new File(URLDecoder.decode(resource.getFile(), "utf-8"));
            if (!classworldsConf.exists()) {
                listener.error(
                        "Unable to locate classworlds configuration file under " + classworldsConf.getAbsolutePath());
                throw new Run.RunnerAbortedException();
            }
        } else {
            classworldsConf = new File(mvn.getHome(), "bin/m2.conf");
        }

        args.add("-Dclassworlds.conf=" + classworldsConf.getCanonicalPath());

        // maven opts
        String mavenOpts = Util.replaceMacro(getMavenOpts(), build.getBuildVariableResolver());
        if (StringUtils.isNotBlank(mavenOpts)) {
            args.add(mavenOpts);
        }

        // classworlds launcher main class
        args.add(CLASSWORLDS_LAUNCHER);

        // pom file to build
        String rootPom = getRootPom();
        if (StringUtils.isNotBlank(rootPom)) {
            args.add("-f", rootPom);
        }

        // maven goals
        args.addTokenized(getGoals());

        return args;
    }

    public Maven.MavenInstallation getMavenInstallation() {
        Maven.MavenInstallation[] installations = getDescriptor().getInstallations();
        for (Maven.MavenInstallation installation : installations) {
            if (installation.getName().equals(mavenName)) {
                return installation;
            }
        }
        // not found, return the first installation if exists
        return installations.length > 0 ? installations[0] : null;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        public DescriptorImpl() {
            load();
        }

        protected DescriptorImpl(Class<? extends Maven3Builder> clazz) {
            super(clazz);
        }

        /**
         * Obtains the {@link hudson.tasks.Maven.MavenInstallation.DescriptorImpl} instance.
         */
        public Maven.MavenInstallation.DescriptorImpl getToolDescriptor() {
            return ToolInstallation.all().get(Maven.MavenInstallation.DescriptorImpl.class);
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getHelpFile() {
            return "/plugin/artifactory/maven3/help.html";
        }

        @Override
        public String getDisplayName() {
            return Messages.step_displayName();
        }

        public Maven.DescriptorImpl getMavenDescriptor() {
            return Hudson.getInstance().getDescriptorByType(Maven.DescriptorImpl.class);
        }

        public Maven.MavenInstallation[] getInstallations() {
            return getMavenDescriptor().getInstallations();
        }

        @Override
        public Maven3Builder newInstance(StaplerRequest request, JSONObject formData) throws FormException {
            return (Maven3Builder) request.bindJSON(clazz, formData);
        }
    }
}