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

import com.google.common.collect.Iterables;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.FreeStyleProject;
import hudson.model.Hudson;
import hudson.model.Project;
import hudson.model.Result;
import hudson.remoting.Which;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.tasks.Maven;
import hudson.util.FormValidation;
import hudson.util.XStream2;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jfrog.build.extractor.maven.BuildInfoRecorder;
import org.jfrog.hudson.ArtifactoryBuilder;
import org.jfrog.hudson.ArtifactoryServer;
import org.jfrog.hudson.BuildInfoResultAction;
import org.jfrog.hudson.DeployerOverrider;
import org.jfrog.hudson.ServerDetails;
import org.jfrog.hudson.action.ActionableHelper;
import org.jfrog.hudson.util.BuildContext;
import org.jfrog.hudson.util.Credentials;
import org.jfrog.hudson.util.ExtractorUtils;
import org.jfrog.hudson.util.FormValidations;
import org.jfrog.hudson.util.IncludesExcludes;
import org.jfrog.hudson.util.OverridingDeployerCredentialsConverter;
import org.jfrog.hudson.util.PluginDependencyHelper;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author Noam Y. Tenne
 */
public class ArtifactoryMaven3Configurator extends BuildWrapper implements DeployerOverrider {
    /**
     * Repository URL and repository to deploy artifacts to
     */
    private final ServerDetails details;
    private final Credentials overridingDeployerCredentials;
    /**
     * If checked (default) deploy maven artifacts
     */
    private final boolean deployArtifacts;
    private final IncludesExcludes artifactDeploymentPatterns;

    /**
     * Include environment variables in the generated build info
     */
    private final boolean includeEnvVars;

    private final boolean deployBuildInfo;
    private final boolean runChecks;

    private final String violationRecipients;

    private final boolean includePublishArtifacts;

    private final String scopes;

    private boolean licenseAutoDiscovery;
    private boolean disableLicenseAutoDiscovery;
    private final boolean discardOldBuilds;
    private final boolean discardBuildArtifacts;
    private final String matrixParams;

    @DataBoundConstructor
    public ArtifactoryMaven3Configurator(ServerDetails details, Credentials overridingDeployerCredentials,
            IncludesExcludes artifactDeploymentPatterns, boolean deployArtifacts, boolean deployBuildInfo,
            boolean includeEnvVars, boolean runChecks, String violationRecipients, boolean includePublishArtifacts,
            String scopes, boolean disableLicenseAutoDiscovery, boolean discardOldBuilds,
            boolean discardBuildArtifacts, String matrixParams) {
        this.details = details;
        this.overridingDeployerCredentials = overridingDeployerCredentials;
        this.artifactDeploymentPatterns = artifactDeploymentPatterns;
        this.runChecks = runChecks;
        this.violationRecipients = violationRecipients;
        this.includePublishArtifacts = includePublishArtifacts;
        this.scopes = scopes;
        this.discardOldBuilds = discardOldBuilds;
        this.discardBuildArtifacts = discardBuildArtifacts;
        this.matrixParams = matrixParams;
        this.licenseAutoDiscovery = !disableLicenseAutoDiscovery;
        this.deployBuildInfo = deployBuildInfo;
        this.deployArtifacts = deployArtifacts;
        this.includeEnvVars = includeEnvVars;
    }

    // NOTE: The following getters are used by jelly. Do not remove them

    public ServerDetails getDetails() {
        return details;
    }

    public boolean isDiscardOldBuilds() {
        return discardOldBuilds;
    }

    public boolean isDiscardBuildArtifacts() {
        return discardBuildArtifacts;
    }

    public boolean isOverridingDefaultDeployer() {
        return (getOverridingDeployerCredentials() != null);
    }

    public Credentials getOverridingDeployerCredentials() {
        return overridingDeployerCredentials;
    }

    public boolean isDeployArtifacts() {
        return deployArtifacts;
    }

    public String getMatrixParams() {
        return matrixParams;
    }

    public IncludesExcludes getArtifactDeploymentPatterns() {
        return artifactDeploymentPatterns;
    }

    public boolean isDeployBuildInfo() {
        return deployBuildInfo;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public String getRepositoryKey() {
        return details != null ? details.repositoryKey : null;
    }

    public boolean isIncludePublishArtifacts() {
        return includePublishArtifacts;
    }

    /**
     * @return The snapshots deployment repository. If not defined the releases deployment repository will be returned
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public String getSnapshotsRepositoryKey() {
        return details != null ?
                (details.snapshotsRepositoryKey != null ? details.snapshotsRepositoryKey : details.repositoryKey) :
                null;
    }

    public boolean isLicenseAutoDiscovery() {
        return licenseAutoDiscovery;
    }

    public boolean isDisableLicenseAutoDiscovery() {
        return disableLicenseAutoDiscovery;
    }

    public String getArtifactoryName() {
        return details != null ? details.artifactoryName : null;
    }

    public String getScopes() {
        return scopes;
    }

    public boolean isIncludeEnvVars() {
        return includeEnvVars;
    }

    public String getViolationRecipients() {
        return violationRecipients;
    }

    public boolean isRunChecks() {
        return runChecks;
    }

    public ArtifactoryServer getArtifactoryServer(String artifactoryServerName) {
        List<ArtifactoryServer> servers = getDescriptor().getArtifactoryServers();
        for (ArtifactoryServer server : servers) {
            if (server.getName().equals(artifactoryServerName)) {
                return server;
            }
        }
        return null;
    }

    @Override
    public Collection<? extends Action> getProjectActions(AbstractProject project) {
        return ActionableHelper.getArtifactoryProjectAction(details.artifactoryName, project);
    }

    @Override
    public Environment setUp(final AbstractBuild build, Launcher launcher, final BuildListener listener)
            throws IOException, InterruptedException {

        final String artifactoryServerName = getArtifactoryName();
        if (StringUtils.isBlank(artifactoryServerName)) {
            return super.setUp(build, launcher, listener);
        }
        final ArtifactoryServer artifactoryServer = getArtifactoryServer(artifactoryServerName);
        if (artifactoryServer == null) {
            listener.getLogger().format("No Artifactory server configured for %s. " +
                    "Please check your configuration.", artifactoryServerName).println();
            build.setResult(Result.FAILURE);
            throw new IllegalArgumentException("No Artifactory server configured for " + artifactoryServerName);
        }
        final Maven maven = getLastMaven(build.getProject());
        String mavenOpts = getMavenOpts(maven);
        String targets = getMavenTargets(maven);
        final File classWorldsFile = File.createTempFile("classworlds", "conf");
        final BuildContext context = new BuildContext(getDetails(), ArtifactoryMaven3Configurator.this, isRunChecks(),
                isIncludePublishArtifacts(), getViolationRecipients(), getScopes(), isLicenseAutoDiscovery(),
                isDiscardOldBuilds(), isDeployArtifacts(), getArtifactDeploymentPatterns(), skipBuildInfoDeploy,
                isIncludeEnvVars(), isDiscardBuildArtifacts(), getMatrixParams());
        URL resource = getClass().getClassLoader().getResource("org/jfrog/hudson/maven3/classworlds-freestyle.conf");
        final String classworldsConfPath = ExtractorUtils.copyClassWorldsFile(build, resource, classWorldsFile);
        final String finalMavenOpts = mavenOpts;
        final String finalTargets = targets;
        if (maven != null) {
            FilePath mavenHomeDir =
                    ExtractorUtils.getMavenHomeDir(build, listener, build.getEnvironment(listener), maven.getMaven());
            setOptsField(maven,
                    createNewMavenOpts(finalMavenOpts, build, classworldsConfPath, mavenHomeDir, listener));
            setTargetsField(maven, finalTargets + " " + "-Dclassworlds.conf=" + classworldsConfPath);
        }
        build.setResult(Result.SUCCESS);
        return new Environment() {
            @Override
            public void buildEnvVars(Map<String, String> env) {
                try {
                    ExtractorUtils.addCustomClassworlds(env, classworldsConfPath);
                    ExtractorUtils.addBuilderInfoArguments(env, build, artifactoryServer, context);
                } catch (Exception e) {
                    listener.getLogger().
                            format("Failed to collect Artifactory Build Info to properties file: %s", e.getMessage()).
                            println();
                    build.setResult(Result.FAILURE);
                    org.apache.commons.io.FileUtils.deleteQuietly(classWorldsFile);
                    throw new RuntimeException(e);
                }
            }

            @Override
            public boolean tearDown(AbstractBuild build, BuildListener listener)
                    throws IOException, InterruptedException {
                org.apache.commons.io.FileUtils.deleteQuietly(classWorldsFile);
                if (maven != null) {
                    setOptsField(maven, finalMavenOpts);
                    setTargetsField(maven, finalTargets);
                }
                Result result = build.getResult();
                if (result == null || result.isWorseThan(Result.SUCCESS)) {
                    return false;
                }
                if (deployBuildInfo) {
                    build.getActions().add(new BuildInfoResultAction(getArtifactoryName(), build));
                }
                return true;
            }
        };
    }

    private Maven getLastMaven(AbstractProject project) {
        if (project instanceof Project) {
            List<Maven> mavens = ActionableHelper.getBuilder((Project) project, Maven.class);
            return Iterables.getLast(mavens, null);
        }
        return null;
    }


    private String createNewMavenOpts(String originalMavenOpts, AbstractBuild build, String classWorldsConfPath,
            FilePath mavenHomeDir, BuildListener listener) throws IOException, InterruptedException {
        StringBuilder builder = new StringBuilder(originalMavenOpts).append(" ");
        builder.append("classworlds.conf=").append(classWorldsConfPath).append(" ");
        builder.append(appendNewMavenOpts(build, originalMavenOpts)).append(" ");
        return builder.toString();
    }


    private String getMavenOpts(Maven maven) {
        if (maven != null && StringUtils.isNotBlank(maven.jvmOptions)) {
            return maven.jvmOptions;
        }
        return "";
    }

    private String getMavenTargets(Maven maven) {
        if (maven != null && StringUtils.isNotBlank(maven.targets)) {
            return maven.targets;
        }
        return "";
    }

    private void setOptsField(Maven builder, String opts) {
        try {
            Field targetsField = builder.getClass().getDeclaredField("jvmOptions");
            targetsField.setAccessible(true);
            targetsField.set(builder, opts);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String appendNewMavenOpts(AbstractBuild build, String opts) throws IOException {
        StringBuilder mavenOpts = new StringBuilder();
        if (StringUtils.contains(opts, "-Dm3plugin.lib")) {
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

    private void setTargetsField(Maven builder, String targets) {
        try {
            Field targetsField = builder.getClass().getDeclaredField("targets");
            targetsField.setAccessible(true);
            targetsField.set(builder, targets);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension(optional = true)
    public static class DescriptorImpl extends BuildWrapperDescriptor {
        public DescriptorImpl() {
            super(ArtifactoryMaven3Configurator.class);
            load();
        }

        @Override
        public boolean isApplicable(AbstractProject<?, ?> item) {
            return item.getClass().isAssignableFrom(FreeStyleProject.class);
        }

        @Override
        public String getDisplayName() {
            return "Maven3-Artifactory Integration (deprecated)";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            req.bindParameters(this, "maven3");
            save();
            return true;
        }

        public FormValidation doCheckViolationRecipients(@QueryParameter String value) {
            return FormValidations.validateEmails(value);
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

    /**
     * Convert any remaining local credential variables to a credentials object
     */
    public static final class ConverterImpl extends OverridingDeployerCredentialsConverter {
        public ConverterImpl(XStream2 xstream) {
            super(xstream);
        }
    }

    /**
     * @deprecated: Use org.jfrog.hudson.DeployerOverrider#getOverridingDeployerCredentials()
     */
    @Deprecated
    private transient String username;

    /**
     * @deprecated: Use org.jfrog.hudson.DeployerOverrider#getOverridingDeployerCredentials()
     */
    @Deprecated
    private transient String scrambledPassword;

    /**
     * @deprecated: Use org.jfrog.hudson.maven3.ArtifactoryMaven3Configurator#deployBuildInfo
     */
    @Deprecated
    private transient boolean skipBuildInfoDeploy;

}
