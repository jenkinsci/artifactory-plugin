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

package org.jfrog.hudson.ivy;

import com.google.common.collect.Maps;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.ivy.AntIvyBuildWrapper;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Hudson;
import hudson.model.Result;
import hudson.remoting.Which;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.tasks.LogRotator;
import hudson.util.FormValidation;
import hudson.util.XStream2;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.aspectj.weaver.loadtime.Agent;
import org.jfrog.build.api.BuildInfoConfigProperties;
import org.jfrog.build.api.BuildInfoProperties;
import org.jfrog.build.client.ClientIvyProperties;
import org.jfrog.build.client.ClientProperties;
import org.jfrog.build.config.ArtifactoryIvySettingsConfigurator;
import org.jfrog.hudson.ArtifactoryBuilder;
import org.jfrog.hudson.ArtifactoryServer;
import org.jfrog.hudson.DeployerOverrider;
import org.jfrog.hudson.ServerDetails;
import org.jfrog.hudson.action.ActionableHelper;
import org.jfrog.hudson.util.CredentialResolver;
import org.jfrog.hudson.util.Credentials;
import org.jfrog.hudson.util.FormValidations;
import org.jfrog.hudson.util.IncludesExcludes;
import org.jfrog.hudson.util.OverridingDeployerCredentialsConverter;
import org.jfrog.hudson.util.PluginDependencyHelper;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;


/**
 * @author Tomer Cohen
 */
public class ArtifactoryIvyConfigurator extends AntIvyBuildWrapper implements DeployerOverrider {

    private ServerDetails details;
    private final Credentials overridingDeployerCredentials;
    private boolean deployArtifacts;
    private final IncludesExcludes artifactDeploymentPatterns;
    private boolean deployBuildInfo;
    private boolean includeEnvVars;
    private boolean runChecks;
    private String violationRecipients;
    private boolean includePublishArtifacts;
    private String scopes;
    private boolean licenseAutoDiscovery;
    private boolean disableLicenseAutoDiscovery;
    private boolean notM2Compatible;
    private String ivyPattern;
    private String artifactPattern;

    @DataBoundConstructor
    public ArtifactoryIvyConfigurator(ServerDetails details, Credentials overridingDeployerCredentials,
            boolean deployArtifacts, IncludesExcludes artifactDeploymentPatterns, boolean deployBuildInfo,
            boolean includeEnvVars, boolean runChecks, String violationRecipients, boolean includePublishArtifacts,
            String scopes, boolean disableLicenseAutoDiscovery, boolean notM2Compatible, String ivyPattern,
            String artifactPattern) {
        this.details = details;
        this.overridingDeployerCredentials = overridingDeployerCredentials;
        this.deployArtifacts = deployArtifacts;
        this.artifactDeploymentPatterns = artifactDeploymentPatterns;
        this.deployBuildInfo = deployBuildInfo;
        this.includeEnvVars = includeEnvVars;
        this.runChecks = runChecks;
        this.violationRecipients = violationRecipients;
        this.includePublishArtifacts = includePublishArtifacts;
        this.scopes = scopes;
        this.disableLicenseAutoDiscovery = disableLicenseAutoDiscovery;
        this.notM2Compatible = notM2Compatible;
        this.ivyPattern = ivyPattern;
        this.artifactPattern = artifactPattern;
        this.licenseAutoDiscovery = !disableLicenseAutoDiscovery;
    }

    public ServerDetails getDetails() {
        return details;
    }

    public boolean isOverridingDefaultDeployer() {
        return (getOverridingDeployerCredentials() != null);
    }

    public Credentials getOverridingDeployerCredentials() {
        return overridingDeployerCredentials;
    }

    public boolean isNotM2Compatible() {
        return notM2Compatible;
    }

    public void setNotM2Compatible(boolean notM2Compatible) {
        this.notM2Compatible = notM2Compatible;
    }

    public String getArtifactPattern() {
        return artifactPattern;
    }

    public void setArtifactPattern(String artifactPattern) {
        this.artifactPattern = artifactPattern;
    }

    public String getIvyPattern() {
        return ivyPattern;
    }

    public void setIvyPattern(String ivyPattern) {
        this.ivyPattern = ivyPattern;
    }

    public boolean isM2Compatible() {
        return !notM2Compatible;
    }

    public boolean isIncludePublishArtifacts() {
        return includePublishArtifacts;
    }

    public void setIncludePublishArtifacts(boolean includePublishArtifacts) {
        this.includePublishArtifacts = includePublishArtifacts;
    }

    public boolean isRunChecks() {
        return runChecks;
    }

    public boolean isDisableLicenseAutoDiscovery() {
        return disableLicenseAutoDiscovery;
    }

    public void setLicenseAutoDiscovery(boolean licenseAutoDiscovery) {
        this.licenseAutoDiscovery = licenseAutoDiscovery;
    }

    public String getScopes() {
        return scopes;
    }

    public void setRunChecks(boolean runChecks) {
        this.runChecks = runChecks;
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

    public boolean isIncludeEnvVars() {
        return includeEnvVars;
    }

    public String getArtifactoryName() {
        return details != null ? details.artifactoryName : null;
    }

    public String getRepositoryKey() {
        return details != null ? details.repositoryKey : null;
    }

    public void setViolationRecipients(String violationRecipients) {
        this.violationRecipients = violationRecipients;
    }

    public String getViolationRecipients() {
        return violationRecipients;
    }

    @Override
    public Collection<? extends Action> getProjectActions(AbstractProject project) {
        return ActionableHelper.getArtifactoryProjectAction(details.artifactoryName, project);
    }

    @Override
    public Environment setUp(final AbstractBuild build, Launcher launcher, BuildListener listener)
            throws IOException, InterruptedException {
        final ArtifactoryServer artifactoryServer = getArtifactoryServer();
        build.setResult(Result.SUCCESS);

        File localDependencyFile = Which.jarFile(ArtifactoryIvySettingsConfigurator.class);
        final FilePath actualDependencyDir =
                PluginDependencyHelper.getActualDependencyDirectory(build, localDependencyFile);

        return new AntIvyBuilderEnvironment() {
            @Override
            public void buildEnvVars(Map<String, String> env) {
                Map<String, String> envVars = Maps.newHashMap();
                for (Map.Entry<String, String> entry : env.entrySet()) {
                    envVars.put(BuildInfoProperties.BUILD_INFO_ENVIRONMENT_PREFIX + entry.getKey(), entry.getValue());
                }
                env.putAll(envVars);
                env.put(ClientProperties.PROP_CONTEXT_URL, artifactoryServer.getUrl());
                env.put(ClientProperties.PROP_PUBLISH_REPOKEY, getRepositoryKey());

                Credentials preferredDeployer = CredentialResolver.getPreferredDeployer(ArtifactoryIvyConfigurator.this,
                        artifactoryServer);
                env.put(ClientProperties.PROP_PUBLISH_USERNAME, preferredDeployer.getUsername());
                env.put(ClientProperties.PROP_PUBLISH_PASSWORD, preferredDeployer.getPassword());
                env.put(BuildInfoProperties.PROP_AGENT_NAME, "Hudson");
                env.put(BuildInfoProperties.PROP_AGENT_VERSION, build.getHudsonVersion());
                env.put(BuildInfoProperties.PROP_BUILD_NUMBER, build.getNumber() + "");
                env.put(BuildInfoProperties.PROP_BUILD_NAME, build.getProject().getName());
                String principal = "auto";
                CauseAction action = ActionableHelper.getLatestAction(build, CauseAction.class);
                if (action != null) {
                    for (Cause cause : action.getCauses()) {
                        if (cause instanceof Cause.UserCause) {
                            principal = ((Cause.UserCause) cause).getUserName();
                        }
                    }
                }
                env.put(BuildInfoProperties.PROP_PRINCIPAL, principal);
                env.put(BuildInfoConfigProperties.PROP_INCLUDE_ENV_VARS, String.valueOf(isIncludeEnvVars()));
                env.put(ClientProperties.PROP_PUBLISH_BUILD_INFO, String.valueOf(isDeployBuildInfo()));
                env.put(ClientProperties.PROP_PUBLISH_ARTIFACT, String.valueOf(isDeployArtifacts()));
                env.put(ClientIvyProperties.PROP_M2_COMPATIBLE, String.valueOf(isM2Compatible()));
                if (StringUtils.isNotBlank(getIvyPattern())) {
                    env.put(ClientIvyProperties.PROP_IVY_IVY_PATTERN, normalizeString(getIvyPattern()));
                }
                if (StringUtils.isNotBlank(getArtifactPattern())) {
                    env.put(ClientIvyProperties.PROP_IVY_ARTIFACT_PATTERN, normalizeString(getArtifactPattern()));
                }

                IncludesExcludes deploymentPatterns = getArtifactDeploymentPatterns();
                if (deploymentPatterns != null) {
                    String includePatterns = deploymentPatterns.getIncludePatterns();
                    if (StringUtils.isNotBlank(includePatterns)) {
                        env.put(ClientProperties.PROP_PUBLISH_ARTIFACT_INCLUDE_PATTERNS, includePatterns);
                    }

                    String excludePatterns = deploymentPatterns.getExcludePatterns();
                    if (StringUtils.isNotBlank(excludePatterns)) {
                        env.put(ClientProperties.PROP_PUBLISH_ARTIFACT_EXCLUDE_PATTERNS, excludePatterns);
                    }
                }
                String buildUrl = ActionableHelper.getBuildUrl(build);
                if (StringUtils.isNotBlank(buildUrl)) {
                    env.put(BuildInfoProperties.PROP_BUILD_URL, buildUrl);
                }
                Cause.UpstreamCause parent = ActionableHelper.getUpstreamCause(build);
                if (parent != null) {
                    env.put(BuildInfoProperties.PROP_PARENT_BUILD_NAME, parent.getUpstreamProject());
                    env.put(BuildInfoProperties.PROP_PARENT_BUILD_NUMBER, parent.getUpstreamBuild() + "");
                }
                env.put(BuildInfoProperties.PROP_LICENSE_CONTROL_RUN_CHECKS, String.valueOf(isRunChecks()));
                env.put(BuildInfoProperties.PROP_LICENSE_CONTROL_AUTO_DISCOVER,
                        String.valueOf(licenseAutoDiscovery));
                env.put(BuildInfoProperties.PROP_LICENSE_CONTROL_INCLUDE_PUBLISHED_ARTIFACTS,
                        String.valueOf(isIncludePublishArtifacts()));
                if (StringUtils.isNotBlank(getViolationRecipients())) {
                    env.put(BuildInfoProperties.PROP_LICENSE_CONTROL_VIOLATION_RECIPIENTS, getViolationRecipients());
                }
                if (StringUtils.isNotBlank(getScopes())) {
                    env.put(BuildInfoProperties.PROP_LICENSE_CONTROL_SCOPES, getScopes());
                }
                LogRotator rotator = build.getProject().getLogRotator();
                if (rotator != null) {
                    if (rotator.getNumToKeep() > -1) {
                        env.put(BuildInfoProperties.PROP_BUILD_RETENTION_DAYS, String.valueOf(rotator.getNumToKeep()));
                    }
                    if (rotator.getDaysToKeep() > -1) {
                        env.put(BuildInfoProperties.PROP_BUILD_RETENTION_MINIMUM_DATE,
                                String.valueOf(rotator.getDaysToKeep()));
                    }
                }
            }

            private String normalizeString(String text) {
                text = StringUtils.removeStart(text, "\"");
                return StringUtils.removeEnd(text, "\"");
            }

            @Override
            public String getAdditionalOpts() {
                File agentLib;
                try {
                    agentLib = Which.jarFile(Agent.class);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                StringBuilder extraAntOpts = new StringBuilder();

                String actualAgentLibPath = actualDependencyDir.child(agentLib.getName()).getRemote();
                actualAgentLibPath = actualAgentLibPath.replace('\\', '/');
                actualAgentLibPath = "\"" + actualAgentLibPath + "\"";
                extraAntOpts.append("-javaagent:").append(actualAgentLibPath).append(" ");
                return extraAntOpts.toString();
            }

            @Override
            public String getAdditionalArgs() {
                StringBuilder targets = new StringBuilder();
                String actualDependencyDirPath = actualDependencyDir.getRemote();
                actualDependencyDirPath = actualDependencyDirPath.replace('\\', '/');
                actualDependencyDirPath = "\"" + actualDependencyDirPath + "\"";
                targets.append("-lib ").append(actualDependencyDirPath).append(" ");
                return targets.toString();
            }
        };
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
            super(ArtifactoryIvyConfigurator.class);
            load();
        }

        @Override
        public boolean isApplicable(AbstractProject<?, ?> item) {
            return "hudson.ivy.IvyModuleSet".equals(item.getClass().getName());
        }

        @Override
        public String getDisplayName() {
            return "Publish to Artifactory";
        }

        @Override
        public String getHelpFile() {
            return "/plugin/artifactory/ivy/help-publish.html";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            req.bindParameters(this, "ivy");
            save();
            return true;
        }

        public FormValidation doCheckArtifactoryName(@QueryParameter String value) {
            return FormValidations.validateInternetAddress(value);
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
    private transient String password;
}
