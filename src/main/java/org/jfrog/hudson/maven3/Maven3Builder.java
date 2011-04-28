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
import hudson.model.*;
import hudson.remoting.VirtualChannel;
import hudson.remoting.Which;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.slaves.SlaveComputer;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.tasks.Maven;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolLocationNodeProperty;
import hudson.util.ArgumentListBuilder;
import hudson.util.DescribableList;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jfrog.build.api.BuildInfoConfigProperties;
import org.jfrog.build.extractor.maven.Maven3BuildInfoLogger;
import org.jfrog.hudson.util.PluginDependencyHelper;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.List;

/**
 * Maven3 builder. Hudson 1.392 added native support for maven 3 but this one is useful for free style.
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
        ArgumentListBuilder cmdLine = buildMavenCmdLine(build, listener, env);
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

    private ArgumentListBuilder buildMavenCmdLine(AbstractBuild<?, ?> build, BuildListener listener,
            EnvVars env) throws IOException, InterruptedException {

        FilePath mavenHome = getMavenHomeDir(build, listener, env);

        if (!mavenHome.exists()) {
            listener.error("Couldn't find Maven home: " + mavenHome.getRemote());
            throw new Run.RunnerAbortedException();
        }

        ArgumentListBuilder args = new ArgumentListBuilder();

        FilePath mavenBootDir = new FilePath(mavenHome, "boot");
        FilePath[] classworldsCandidates = mavenBootDir.list("plexus-classworlds*.jar");
        if (classworldsCandidates == null || classworldsCandidates.length == 0) {
            listener.error("Couldn't find classworlds jar under " + mavenBootDir.getRemote());
            throw new Run.RunnerAbortedException();
        }

        FilePath classWorldsJar = classworldsCandidates[0];

        // classpath
        args.add("-classpath");
        //String cpSeparator = launcher.isUnix() ? ":" : ";";

        args.add(new StringBuilder().append(classWorldsJar.getRemote()).toString());

        // maven opts
        args.addTokenized(getMavenOpts());

        String buildInfoPropertiesFile = env.get(BuildInfoConfigProperties.PROP_PROPS_FILE);
        boolean artifactoryIntegration = StringUtils.isNotBlank(buildInfoPropertiesFile);
        if (artifactoryIntegration) {
            args.add(new StringBuilder("-D").append(BuildInfoConfigProperties.PROP_PROPS_FILE + "=").append(
                    buildInfoPropertiesFile).toString());
        }

        // maven home
        args.add("-Dmaven.home=" + mavenHome.getRemote());

        String classworldsConfPath;
        if (artifactoryIntegration) {

            // use the classworlds conf packaged with this plugin and resolve the extractor libs
            File maven3ExtractorJar = Which.jarFile(Maven3BuildInfoLogger.class);
            FilePath actualDependencyDirectory =
                    PluginDependencyHelper.getActualDependencyDirectory(build, maven3ExtractorJar);

            args.add("-Dm3plugin.lib=" + actualDependencyDirectory.getRemote());

            URL classworldsResource =
                    getClass().getClassLoader().getResource("org/jfrog/hudson/maven3/classworlds-freestyle.conf");

            File classworldsConfFile = new File(URLDecoder.decode(classworldsResource.getFile(), "utf-8"));
            if (!classworldsConfFile.exists()) {
                listener.error("Unable to locate classworlds configuration file under " +
                        classworldsConfFile.getAbsolutePath());
                throw new Run.RunnerAbortedException();
            }

            //If we are on a remote slave, make a temp copy of the customized classworlds conf
            if (Computer.currentComputer() instanceof SlaveComputer) {

                FilePath remoteClassworlds = build.getWorkspace().createTextTempFile("classworlds", "conf", "", false);
                remoteClassworlds.copyFrom(classworldsResource);
                classworldsConfPath = remoteClassworlds.getRemote();
            } else {
                classworldsConfPath = classworldsConfFile.getCanonicalPath();
            }
        } else {
            classworldsConfPath = new FilePath(mavenHome, "bin/m2.conf").getRemote();
        }

        args.add("-Dclassworlds.conf=" + classworldsConfPath);

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

    private FilePath getMavenHomeDir(AbstractBuild<?, ?> build, BuildListener listener, EnvVars env) {
        Computer computer = Computer.currentComputer();
        VirtualChannel virtualChannel = computer.getChannel();

        String mavenHome = null;

        //Check for a node defined tool if we are on a slave
        if (computer instanceof SlaveComputer) {
            mavenHome = getNodeDefinedMavenHome(build);
        }

        //Either we are on the master or that no node tool was defined
        if (StringUtils.isBlank(mavenHome)) {
            mavenHome = getJobDefinedMavenInstallation(listener, virtualChannel);
        }

        //Try to find the home via the env vars
        if (StringUtils.isBlank(mavenHome)) {
            mavenHome = getEnvDefinedMavenHome(env);
        }
        return new FilePath(virtualChannel, mavenHome);
    }

    private String getNodeDefinedMavenHome(AbstractBuild<?, ?> build) {
        Node currentNode = build.getBuiltOn();
        DescribableList<NodeProperty<?>, NodePropertyDescriptor> properties = currentNode.getNodeProperties();
        ToolLocationNodeProperty toolLocation = properties.get(ToolLocationNodeProperty.class);
        if (toolLocation != null) {

            List<ToolLocationNodeProperty.ToolLocation> locations = toolLocation.getLocations();
            if (locations != null) {
                for (ToolLocationNodeProperty.ToolLocation location : locations) {
                    if (location.getType().isSubTypeOf(Maven.MavenInstallation.class)) {
                        String installationHome = location.getHome();
                        if (StringUtils.isNotBlank(installationHome)) {
                            return installationHome;
                        }
                    }
                }
            }
        }
        return null;
    }

    private String getEnvDefinedMavenHome(EnvVars env) {
        String mavenHome = env.get("MAVEN_HOME");
        if (StringUtils.isNotBlank(mavenHome)) {
            return mavenHome;
        }

        return env.get("M2_HOME");
    }

    private String getJobDefinedMavenInstallation(BuildListener listener, VirtualChannel channel) {
        Maven.MavenInstallation mvn = getMavenInstallation();
        if (mvn == null) {
            listener.error("Maven version is not configured for this project. Can't determine which Maven to run");
            throw new Run.RunnerAbortedException();
        }
        String mvnHome = mvn.getHome();
        if (mvnHome == null) {
            listener.error("Maven '%s' doesn't have its home set", mvn.getName());
            throw new Run.RunnerAbortedException();
        }
        return mvnHome;
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
            return jobType.equals(FreeStyleProject.class);
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
