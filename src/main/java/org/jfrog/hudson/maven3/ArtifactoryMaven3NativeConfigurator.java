package org.jfrog.hudson.maven3;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.maven.MavenModuleSet;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.Hudson;
import hudson.model.Result;
import hudson.remoting.Which;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.tasks.Maven;
import net.sf.json.JSONObject;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.jfrog.build.api.util.NullLog;
import org.jfrog.build.client.ArtifactoryClientConfiguration;
import org.jfrog.build.extractor.maven.BuildInfoRecorder;
import org.jfrog.hudson.ArtifactoryBuilder;
import org.jfrog.hudson.ArtifactoryServer;
import org.jfrog.hudson.ResolverOverrider;
import org.jfrog.hudson.ServerDetails;
import org.jfrog.hudson.util.CredentialResolver;
import org.jfrog.hudson.util.Credentials;
import org.jfrog.hudson.util.ExtractorUtils;
import org.jfrog.hudson.util.PluginDependencyHelper;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Tomer Cohen
 */
public class ArtifactoryMaven3NativeConfigurator extends BuildWrapper implements ResolverOverrider {

    /**
     * The minimum Maven version that is required for the {@link AbstractRepositoryListener} to be present.
     */
    private static final String MINIMUM_MAVEN_VERSION = "3.0.2";
    private final ServerDetails details;
    private final Credentials overridingResolverCredentials;

    @DataBoundConstructor
    public ArtifactoryMaven3NativeConfigurator(ServerDetails details, Credentials overridingResolverCredentials) {
        this.details = details;
        this.overridingResolverCredentials = overridingResolverCredentials;
    }

    public ServerDetails getDetails() {
        return details;
    }

    public String getDownloadRepositoryKey() {
        return details != null ? details.downloadRepositoryKey : null;
    }

    public String getArtifactoryName() {
        return details != null ? details.artifactoryName : null;
    }

    public String getRepositoryKey() {
        return details != null ? details.repositoryKey : null;
    }

    @Override
    public Collection<? extends Action> getProjectActions(AbstractProject project) {
        return Collections.emptyList();
    }

    public boolean isOverridingDefaultResolver() {
        return getOverridingResolverCredentials() != null;
    }

    public Credentials getOverridingResolverCredentials() {
        return overridingResolverCredentials;
    }

    @Override
    public Environment setUp(final AbstractBuild build, Launcher launcher, BuildListener listener)
            throws IOException, InterruptedException {
        final MavenModuleSet project = (MavenModuleSet) build.getProject();
        EnvVars envVars = build.getEnvironment(listener);
        // get the maven installation
        Maven.MavenInstallation mavenInstallation = getMavenInstallation(project, envVars, listener);
        boolean isValid =
                build.getWorkspace().act(new MavenVersionCallable(mavenInstallation.getHome(), MINIMUM_MAVEN_VERSION));
        // if the installation is not the minimal required version do nothing.
        if (!(isValid)) {
            return new Environment() {
                // return the empty environment
            };
        }
        final String mavenOpts = project.getMavenOpts();
        try {
            project.setMavenOpts(appendNewMavenOpts(project, build));
        } catch (IOException e) {
            throw new RuntimeException("Unable to manipulate maven_opts for project: " + project, e);
        }
        final File classWorldsFile = File.createTempFile("classworlds", "conf");
        URL resource = getClass().getClassLoader().getResource("org/jfrog/hudson/maven3/classworlds-native.conf");
        final String classworldsConfPath = ExtractorUtils.copyClassWorldsFile(build, resource, classWorldsFile);
        build.setResult(Result.SUCCESS);
        return new Environment() {
            @Override
            public void buildEnvVars(Map<String, String> env) {
                super.buildEnvVars(env);
                final ArtifactoryServer artifactoryServer = getArtifactoryServer();
                Credentials preferredResolver = CredentialResolver
                        .getPreferredResolver(ArtifactoryMaven3NativeConfigurator.this, artifactoryServer);

                ArtifactoryClientConfiguration configuration = new ArtifactoryClientConfiguration(new NullLog());
                configuration.setContextUrl(artifactoryServer.getUrl());
                configuration.resolver.setRepoKey(getDownloadRepositoryKey());
                configuration.resolver.setUsername(preferredResolver.getUsername());
                configuration.resolver.setPassword(preferredResolver.getPassword());
                ExtractorUtils.addBuildRootIfNeeded(build, configuration);
                env.putAll(configuration.getAllProperties());
                ExtractorUtils.addCustomClassworlds(env, classworldsConfPath);
            }

            @Override
            public boolean tearDown(AbstractBuild build, BuildListener listener)
                    throws IOException, InterruptedException {
                final MavenModuleSet project = (MavenModuleSet) build.getProject();
                project.setMavenOpts(mavenOpts);
                FileUtils.deleteQuietly(classWorldsFile);
                return super.tearDown(build, listener);
            }
        };
    }

    /**
     * Get the {@link hudson.model.EnvironmentSpecific} and {@link hudson.slaves.NodeSpecific} Maven installation. First
     * get the descriptor from the global Jenkins. Then populate it accordingly from the specific environment node that
     * the process is currently running in e.g. the MAVEN_HOME variable may be defined only in the remote node and
     * Jenkins is not persisting it as part of its installations.
     *
     * @param project  The Maven project that the maven installation is taken from.
     * @param vars     The build's environment variables.
     * @param listener The build's event listener
     * @throws AbortException If the {@link Maven.MavenInstallation} that is taken from the project is {@code null} then
     *                        this exception is thrown.
     */
    private Maven.MavenInstallation getMavenInstallation(MavenModuleSet project, EnvVars vars, BuildListener listener)
            throws IOException, InterruptedException {
        Maven.MavenInstallation mavenInstallation = project.getMaven();
        if (mavenInstallation == null) {
            throw new AbortException("A Maven installation needs to be available for this project to be built.\n" +
                    "Either your server has no Maven installations defined, or the requested Maven version does not exist.");
        }
        return mavenInstallation.forEnvironment(vars).forNode(Computer.currentComputer().getNode(), listener);
    }

    private String appendNewMavenOpts(MavenModuleSet project, AbstractBuild build) throws IOException {
        StringBuilder mavenOpts = new StringBuilder();
        String opts = project.getMavenOpts();
        if (StringUtils.isNotBlank(opts)) {
            mavenOpts.append(opts);
        }
        if (StringUtils.contains(mavenOpts.toString(), "-Dm3plugin.lib")) {
            return mavenOpts.toString();
        }
        File maven3ExtractorJar = Which.jarFile(BuildInfoRecorder.class);
        try {
            FilePath actualDependencyDirectory =
                    PluginDependencyHelper.getActualDependencyDirectory(build, maven3ExtractorJar);
            mavenOpts.append(" -Dm3plugin.lib=").append(actualDependencyDirectory.getRemote());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return mavenOpts.toString();
    }

    public ArtifactoryServer getArtifactoryServer() {
        List<ArtifactoryServer> servers = getDescriptor().getArtifactoryServers();
        for (ArtifactoryServer server : servers) {
            if (server.getName().equals(getArtifactoryName())) {
                return server;
            }
        }
        return null;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension(optional = true)
    public static class DescriptorImpl extends BuildWrapperDescriptor {
        public DescriptorImpl() {
            super(ArtifactoryMaven3NativeConfigurator.class);
            load();
        }

        @Override
        public boolean isApplicable(AbstractProject<?, ?> item) {
            return MavenModuleSet.class.equals(item.getClass());
        }

        @Override
        public String getDisplayName() {
            return "Resolve artifacts from Artifactory";
        }

        @Override
        public String getHelpFile() {
            return "/plugin/artifactory/help/ArtifactoryMaven3NativeConfigurator/help.html";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            req.bindParameters(this, "maven");
            save();
            return true;
        }

        /**
         * Returns the list of {@link org.jfrog.hudson.ArtifactoryServer} configured.
         *
         * @return can be empty but never null.
         */
        public List<ArtifactoryServer> getArtifactoryServers() {
            ArtifactoryBuilder.DescriptorImpl descriptor = (ArtifactoryBuilder.DescriptorImpl)
                    Hudson.getInstance().getDescriptor(ArtifactoryBuilder.class);
            return descriptor.getArtifactoryServers();
        }
    }
}
