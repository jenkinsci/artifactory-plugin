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

import hudson.*;
import hudson.model.*;
import hudson.remoting.Which;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.tasks.Maven;
import hudson.util.ArgumentListBuilder;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jfrog.build.api.BuildInfoConfigProperties;
import org.jfrog.build.extractor.maven.Maven3BuildInfoLogger;
import org.jfrog.hudson.action.ActionableHelper;
import org.jfrog.hudson.pipeline.Utils;
import org.jfrog.hudson.util.PluginDependencyHelper;
import org.jfrog.hudson.util.plugins.PluginsUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Maven3 builder for free style projects. Hudson 1.392 added native support for maven 3 but this one is useful for free style.
 *
 * @author Yossi Shaul
 */
public class Maven3Builder extends Builder {

    public static final String CLASSWORLDS_LAUNCHER = "org.codehaus.plexus.classworlds.launcher.Launcher";
    public static final String MAVEN_HOME = "MAVEN_HOME";

    private final String mavenName;
    private final String rootPom;
    private final String goals;
    private final String mavenOpts;
    private String classworldsConfPath;

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

    // Used by Generic jobs only
    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException, IOException {
        listener.getLogger().println("Jenkins Artifactory Plugin version: " + ActionableHelper.getArtifactoryPluginVersion());
        EnvVars env = build.getEnvironment(listener);
        FilePath workDir = build.getModuleRoot();
        FilePath ws = build.getWorkspace();
        FilePath mavenHome = getMavenHome(listener, env, launcher);

        if (!mavenHome.exists()) {
            listener.error("Couldn't find Maven home: " + mavenHome.getRemote());
            throw new Run.RunnerAbortedException();
        }
        ArgumentListBuilder cmdLine = buildMavenCmdLine(build, listener, env, launcher, mavenHome, ws, ws);
        String[] cmds = cmdLine.toCommandArray();
        return RunMaven(build, launcher, listener, env, workDir, cmds);
    }

    // Used by Pipeline jobs only
    public boolean perform(Run<?, ?> build, Launcher launcher, TaskListener listener, EnvVars env, FilePath workDir, FilePath tempDir)
            throws InterruptedException, IOException {
        listener.getLogger().println("Jenkins Artifactory Plugin version: " + ActionableHelper.getArtifactoryPluginVersion());
        FilePath mavenHome = getMavenHome(listener, env, launcher);

        if (!mavenHome.exists()) {
            listener.getLogger().println("Couldn't find Maven home at " + mavenHome.getRemote() + " on agent " + Utils.getAgentName(workDir) +
                    ". This could be because this build is running inside a Docker container.");
        }
        ArgumentListBuilder cmdLine = buildMavenCmdLine(build, listener, env, launcher, mavenHome, workDir, tempDir);
        String[] cmds = cmdLine.toCommandArray();
        return RunMaven(build, launcher, listener, env, workDir, cmds);
    }

    private boolean RunMaven(Run<?, ?> build, Launcher launcher, TaskListener listener, EnvVars env, FilePath workDir, String[] cmds) throws InterruptedException, IOException {
        try {
            int exitValue = launcher.launch().cmds(cmds).envs(env).stdout(listener).pwd(workDir).join();
            boolean success = (exitValue == 0);
            build.setResult(success ? Result.SUCCESS : Result.FAILURE);
            return success;
        } catch (IOException e) {
            Util.displayIOException(e, listener);
            e.printStackTrace(listener.fatalError("command execution failed"));
            build.setResult(Result.FAILURE);
            return false;
        } finally {
            ActionableHelper.deleteFilePath(workDir, classworldsConfPath);
        }
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    private ArgumentListBuilder buildMavenCmdLine(Run<?, ?> build, TaskListener listener,
                                                  EnvVars env, Launcher launcher, FilePath mavenHome, FilePath ws, FilePath tempDir)
            throws IOException, InterruptedException {

        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add(getJavaPathBuilder(env.get("PATH+JDK"), launcher));

        // classpath
        Path classPath = Paths.get(mavenHome.getRemote(), "boot", "*");
        args.add("-classpath", classPath.toString());

        // maven home
        args.addKeyValuePair("-D", "maven.home", mavenHome.getRemote(), false);

        String buildInfoPropertiesFile = env.get(BuildInfoConfigProperties.PROP_PROPS_FILE);
        boolean artifactoryIntegration = StringUtils.isNotBlank(buildInfoPropertiesFile);
        listener.getLogger().println("Artifactory integration is " + (artifactoryIntegration ? "enabled" : "disabled"));
        if (artifactoryIntegration) {
            addArtifactoryIntegrationArgs(args, buildInfoPropertiesFile, tempDir);
            ActionableHelper.deleteFilePathOnExit(tempDir, classworldsConfPath);
        } else {
            args.addKeyValuePair("-D", "classworlds.conf", new FilePath(mavenHome, "bin/m2.conf").getRemote(), false);
        }

        //Starting from Maven 3.3.3
        args.addKeyValuePair("-D", "maven.multiModuleProjectDirectory", getMavenProjectPath(build, ws), false);

        addMavenOpts(args, build);

        // classworlds launcher main class
        args.add(CLASSWORLDS_LAUNCHER);

        // pom file to build
        String rootPom = getRootPom();
        if (StringUtils.isNotBlank(rootPom)) {
            args.add("-f", rootPom);
        }

        // maven goals
        args.addTokenized(Util.replaceMacro(getGoals(), env));

        return args;
    }

    private String getJavaPathBuilder(String jdkBinPath, Launcher launcher) {
        StringBuilder javaPathBuilder = new StringBuilder();
        if (StringUtils.isNotBlank(jdkBinPath)) {
            javaPathBuilder.append(jdkBinPath).append("/");
        }
        javaPathBuilder.append("java");
        if (!launcher.isUnix()) {
            javaPathBuilder.append(".exe");
        }
        return javaPathBuilder.toString();
    }

    private void addArtifactoryIntegrationArgs(ArgumentListBuilder args, String buildInfoPropertiesFile, FilePath ws) throws IOException, InterruptedException {
        args.addKeyValuePair("-D", BuildInfoConfigProperties.PROP_PROPS_FILE, buildInfoPropertiesFile, false);

        // use the classworlds conf packaged with this plugin and resolve the extractor libs
        File maven3ExtractorJar = Which.jarFile(Maven3BuildInfoLogger.class);
        FilePath actualDependencyDirectory =
                PluginDependencyHelper.getActualDependencyDirectory(maven3ExtractorJar, ws);

        if (getMavenOpts() == null || !getMavenOpts().contains("-Dm3plugin.lib")) {
            args.addKeyValuePair("-D", "m3plugin.lib", actualDependencyDirectory.getRemote(), false);
        }

        URL resource = getClass().getClassLoader().getResource("org/jfrog/hudson/maven3/classworlds-freestyle.conf");
        classworldsConfPath = copyClassWorldsFile(ws, resource).getRemote();
        args.addKeyValuePair("-D", "classworlds.conf", classworldsConfPath, false);
    }

    private void addMavenOpts(ArgumentListBuilder args, Run<?, ?> build) {
        if (StringUtils.isNotBlank(getMavenOpts())) {
            String mavenOpts = getMavenOpts();
            if (build instanceof AbstractBuild) {
                // If we aren't in pipeline job we, might need to evaluate the variable real value.
                mavenOpts = Util.replaceMacro(getMavenOpts(), ((AbstractBuild) build).getBuildVariables());
            }
            // HAP-314 - We need to separate the args, same as jenkins maven plugin does
            args.addTokenized(mavenOpts);
        }
    }

    private FilePath getMavenHome(TaskListener listener, EnvVars env, Launcher launcher) throws IOException, InterruptedException {
        if (StringUtils.isNotEmpty(mavenName)) {
            Maven.MavenInstallation mi = getMaven();
            if (mi == null) {
                listener.error("Couldn't find Maven executable.");
                throw new Run.RunnerAbortedException();
            } else {
                Node node = ActionableHelper.getNode(launcher);
                mi = mi.forNode(node, listener);
                mi = mi.forEnvironment(env);
            }

            return new FilePath(launcher.getChannel(), mi.getHome());
        }

        if (env.get(MAVEN_HOME) != null) {
            return new FilePath(launcher.getChannel(), env.get(MAVEN_HOME));
        }

        throw new RuntimeException("Couldn't find maven installation");
    }

    private Maven.MavenInstallation getMaven() {
        Maven.MavenInstallation[] installations = getDescriptor().getInstallations();
        for (Maven.MavenInstallation i : installations) {
            if (mavenName != null && mavenName.equals(i.getName())) {
                return i;
            }
        }
        return null;
    }

    private String getMavenProjectPath(Run<?, ?> build, FilePath ws) {
        if (build instanceof AbstractBuild) {
            if (StringUtils.isNotBlank(getRootPom())) {
                return ((AbstractBuild) build).getModuleRoot().getRemote() + File.separatorChar +
                        getRootPom().replace("/pom.xml", StringUtils.EMPTY);
            }
            return ((AbstractBuild) build).getModuleRoot().getRemote();
        }
        return ws.getRemote();
    }

    /**
     * Copies a classworlds file to a temporary location either on the local filesystem or on a slave depending on the
     * node type.
     *
     * @return The path of the classworlds.conf file
     */
    private FilePath copyClassWorldsFile(FilePath ws, URL resource) {
        try {
            FilePath remoteClassworlds =
                    ws.createTextTempFile("classworlds", "conf", "");
            remoteClassworlds.copyFrom(resource);
            return remoteClassworlds;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        public DescriptorImpl() {
            load();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return hudson.model.FreeStyleProject.class.isAssignableFrom(jobType) ||
                    hudson.matrix.MatrixProject.class.isAssignableFrom(jobType) ||
                    (Jenkins.getInstance().getPlugin(PluginsUtils.MULTIJOB_PLUGIN_ID) != null &&
                            com.tikal.jenkins.plugins.multijob.MultiJobProject.class.isAssignableFrom(jobType));
        }

        @Override
        public String getHelpFile() {
            return "/help/project-config/maven.html";
        }

        @Override
        public String getDisplayName() {
            return Messages.step_displayName();
        }

        public Maven.MavenInstallation[] getInstallations() {
            return Jenkins.getInstance().getDescriptorByType(Maven.DescriptorImpl.class).getInstallations();
        }

        @Override
        public Maven3Builder newInstance(StaplerRequest request, JSONObject formData) throws FormException {
            return (Maven3Builder) request.bindJSON(clazz, formData);
        }
    }
}
