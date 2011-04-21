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

import com.google.common.base.Predicate;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.FreeStyleProject;
import hudson.model.Hudson;
import hudson.model.Result;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.FormValidation;
import hudson.util.XStream2;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jfrog.build.api.BuildInfoProperties;
import org.jfrog.build.client.ClientProperties;
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
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * @author Noam Y. Tenne
 * @deprecated Hudson 1.392 added native support for maven 3
 */
@Deprecated
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

    @DataBoundConstructor
    public ArtifactoryMaven3Configurator(ServerDetails details, Credentials overridingDeployerCredentials,
            IncludesExcludes artifactDeploymentPatterns, boolean deployArtifacts, boolean deployBuildInfo,
            boolean includeEnvVars, boolean runChecks, String violationRecipients, boolean includePublishArtifacts,
            String scopes, boolean disableLicenseAutoDiscovery, boolean discardOldBuilds) {
        this.details = details;
        this.overridingDeployerCredentials = overridingDeployerCredentials;
        this.artifactDeploymentPatterns = artifactDeploymentPatterns;
        this.runChecks = runChecks;
        this.violationRecipients = violationRecipients;
        this.includePublishArtifacts = includePublishArtifacts;
        this.scopes = scopes;
        this.discardOldBuilds = discardOldBuilds;
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

    public boolean isOverridingDefaultDeployer() {
        return (getOverridingDeployerCredentials() != null);
    }

    public Credentials getOverridingDeployerCredentials() {
        return overridingDeployerCredentials;
    }

    public boolean isDeployArtifacts() {
        return deployArtifacts;
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

        return new Environment() {
            @Override
            public void buildEnvVars(Map<String, String> env) {

                try {
                    addBuilderInfoArguments(env, build, artifactoryServer);
                } catch (Exception e) {
                    listener.getLogger().
                            format("Failed to collect Artifactory Build Info to properties file: %s", e.getMessage()).
                            println();
                    build.setResult(Result.FAILURE);
                    throw new RuntimeException(e);
                }
            }

            @Override
            public boolean tearDown(AbstractBuild build, BuildListener listener)
                    throws IOException, InterruptedException {
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

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    private void addBuilderInfoArguments(Map<String, String> env, AbstractBuild build,
            ArtifactoryServer selectedArtifactoryServer) throws IOException, InterruptedException {

        final BuildContext context = new BuildContext(getDetails(), this, isRunChecks(),
                isIncludePublishArtifacts(), getViolationRecipients(), getScopes(),
                isLicenseAutoDiscovery(), isDiscardOldBuilds(), isDeployArtifacts(),
                getArtifactDeploymentPatterns(), !isDeployBuildInfo(), isIncludeEnvVars(), true);
        ExtractorUtils.addBuilderInfoArguments(env, build, selectedArtifactoryServer, context);
    }

    private void addEnvVars(Map<String, String> env, AbstractBuild build, Properties props) {
        // Write all the deploy (matrix params) properties.
        Map<String, String> filteredEnvMatrixParams = Maps.filterKeys(env, new Predicate<String>() {
            public boolean apply(String input) {
                return input.startsWith(ClientProperties.PROP_DEPLOY_PARAM_PROP_PREFIX);
            }
        });
        for (Map.Entry<String, String> entry : filteredEnvMatrixParams.entrySet()) {
            props.put(entry.getKey(), entry.getValue());
        }

        //Add only the hudson specific environment variables
        MapDifference<String, String> envDifference = Maps.difference(env, System.getenv());
        Map<String, String> filteredEnvDifference = envDifference.entriesOnlyOnLeft();
        for (Map.Entry<String, String> entry : filteredEnvDifference.entrySet()) {
            props.put(BuildInfoProperties.BUILD_INFO_ENVIRONMENT_PREFIX + entry.getKey(), entry.getValue());
        }

        // add build variables
        Map<String, String> buildVariables = build.getBuildVariables();
        Map<String, String> filteredBuildVars = Maps.newHashMap();

        filteredBuildVars.putAll(Maps.filterKeys(buildVariables, new Predicate<String>() {
            public boolean apply(String input) {
                return input.startsWith(ClientProperties.PROP_DEPLOY_PARAM_PROP_PREFIX);
            }
        }));
        filteredBuildVars.putAll(Maps.filterKeys(buildVariables, new Predicate<String>() {
            public boolean apply(String input) {
                return input.startsWith(BuildInfoProperties.BUILD_INFO_PROP_PREFIX);
            }
        }));

        for (Map.Entry<String, String> filteredBuildVar : filteredBuildVars.entrySet()) {
            props.put(filteredBuildVar.getKey(), filteredBuildVar.getValue());
        }

        MapDifference<String, String> buildVarDifference = Maps.difference(buildVariables, filteredBuildVars);
        Map<String, String> filteredBuildVarDifferences = buildVarDifference.entriesOnlyOnLeft();

        for (Map.Entry<String, String> filteredBuildVarDifference : filteredBuildVarDifferences.entrySet()) {
            props.put(BuildInfoProperties.BUILD_INFO_ENVIRONMENT_PREFIX + filteredBuildVarDifference.getKey(),
                    filteredBuildVarDifference.getValue());
        }
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
