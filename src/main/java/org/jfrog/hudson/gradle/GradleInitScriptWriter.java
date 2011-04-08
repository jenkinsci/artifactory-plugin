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

package org.jfrog.hudson.gradle;

import com.google.common.base.Charsets;
import com.google.common.base.Predicate;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.ProxyConfiguration;
import hudson.model.AbstractBuild;
import hudson.model.Cause;
import hudson.model.Hudson;
import hudson.remoting.Which;
import hudson.tasks.LogRotator;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.jfrog.build.ArtifactoryPluginUtils;
import org.jfrog.build.api.BuildInfoConfigProperties;
import org.jfrog.build.api.BuildInfoProperties;
import org.jfrog.build.client.ClientGradleProperties;
import org.jfrog.build.client.ClientIvyProperties;
import org.jfrog.build.client.ClientProperties;
import org.jfrog.hudson.ArtifactoryServer;
import org.jfrog.hudson.ServerDetails;
import org.jfrog.hudson.action.ActionableHelper;
import org.jfrog.hudson.util.CredentialResolver;
import org.jfrog.hudson.util.Credentials;
import org.jfrog.hudson.util.IncludesExcludes;
import org.jfrog.hudson.util.PluginDependencyHelper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.Map;


/**
 * Class to generate a Gradle initialization script
 *
 * @author Tomer Cohen
 */
public class GradleInitScriptWriter {
    private EnvVars envVars;
    private AbstractBuild build;
    private ArtifactoryGradleConfigurator gradleConfigurator;

    /**
     * The gradle initialization script constructor.
     *
     * @param gradleConfigurator
     * @param build
     */
    public GradleInitScriptWriter(ArtifactoryGradleConfigurator gradleConfigurator, EnvVars envVars,
            AbstractBuild build) {
        this.gradleConfigurator = gradleConfigurator;
        this.envVars = envVars;
        this.build = build;
    }

    private String addProperties() {
        StringBuilder stringBuilder = new StringBuilder();
        String key = ClientProperties.PROP_CONTEXT_URL;
        ArtifactoryServer artifactoryServer = getArtifactoryServer();
        String value = artifactoryServer.getUrl();
        ArtifactoryPluginUtils.addProperty(stringBuilder, key, value);
        ArtifactoryPluginUtils.addProperty(stringBuilder, ClientProperties.PROP_RESOLVE_REPOKEY,
                getServerDetails().downloadRepositoryKey);
        ArtifactoryPluginUtils.addProperty(stringBuilder, ClientProperties.PROP_PUBLISH_REPOKEY,
                getServerDetails().repositoryKey);

        Credentials preferredDeployer = CredentialResolver.getPreferredDeployer(gradleConfigurator, artifactoryServer);
        ArtifactoryPluginUtils.addProperty(stringBuilder, ClientProperties.PROP_PUBLISH_USERNAME,
                preferredDeployer.getUsername());
        ArtifactoryPluginUtils.addProperty(stringBuilder, ClientProperties.PROP_PUBLISH_PASSWORD,
                preferredDeployer.getPassword());
        ArtifactoryPluginUtils.addProperty(stringBuilder, ClientIvyProperties.PROP_PUBLISH_IVY,
                Boolean.toString(gradleConfigurator.deployIvy));
        ArtifactoryPluginUtils.addProperty(stringBuilder, ClientGradleProperties.PROP_PUBLISH_MAVEN,
                Boolean.toString(gradleConfigurator.deployMaven));
        ArtifactoryPluginUtils.addProperty(stringBuilder, ClientProperties.PROP_PUBLISH_ARTIFACT,
                Boolean.toString(gradleConfigurator.isDeployArtifacts()));
        ArtifactoryPluginUtils.addProperty(stringBuilder, ClientProperties.PROP_PUBLISH_BUILD_INFO,
                Boolean.toString(gradleConfigurator.deployBuildInfo));
        ArtifactoryPluginUtils
                .addProperty(stringBuilder, BuildInfoProperties.PROP_BUILD_NAME, build.getProject().getName());
        ArtifactoryPluginUtils
                .addProperty(stringBuilder, BuildInfoProperties.PROP_BUILD_NUMBER, build.getNumber() + "");
        if (StringUtils.isNotBlank(gradleConfigurator.getArtifactPattern())) {
            ArtifactoryPluginUtils.addProperty(stringBuilder, ClientIvyProperties.PROP_IVY_ARTIFACT_PATTERN,
                    gradleConfigurator.getArtifactPattern());
        }
        ArtifactoryPluginUtils.addProperty(stringBuilder, ClientIvyProperties.PROP_M2_COMPATIBLE,
                String.valueOf(gradleConfigurator.isM2Compatible()));
        if (StringUtils.isNotBlank(gradleConfigurator.getIvyPattern())) {
            ArtifactoryPluginUtils.addProperty(stringBuilder, ClientIvyProperties.PROP_IVY_IVY_PATTERN,
                    gradleConfigurator.getIvyPattern());
        }
        ArtifactoryPluginUtils.addProperty(stringBuilder, BuildInfoProperties.PROP_LICENSE_CONTROL_RUN_CHECKS,
                String.valueOf(gradleConfigurator.isRunChecks()));
        if (StringUtils.isNotBlank(gradleConfigurator.getViolationRecipients())) {
            ArtifactoryPluginUtils
                    .addProperty(stringBuilder, BuildInfoProperties.PROP_LICENSE_CONTROL_VIOLATION_RECIPIENTS,
                            gradleConfigurator.getViolationRecipients());
        }
        if (StringUtils.isNotBlank(gradleConfigurator.getScopes())) {
            ArtifactoryPluginUtils.addProperty(stringBuilder, BuildInfoProperties.PROP_LICENSE_CONTROL_SCOPES,
                    gradleConfigurator.getScopes());
        }
        ArtifactoryPluginUtils
                .addProperty(stringBuilder, BuildInfoProperties.PROP_LICENSE_CONTROL_INCLUDE_PUBLISHED_ARTIFACTS,
                        String.valueOf(gradleConfigurator.isIncludePublishArtifacts()));
        ArtifactoryPluginUtils.addProperty(stringBuilder, BuildInfoProperties.PROP_LICENSE_CONTROL_AUTO_DISCOVER,
                String.valueOf(gradleConfigurator.isLicenseAutoDiscovery()));

        ProxyConfiguration proxy = Hudson.getInstance().proxy;
        if (!getArtifactoryServer().isBypassProxy() && proxy != null) {
            ArtifactoryPluginUtils.addProperty(stringBuilder, ClientProperties.PROP_PROXY_HOST, proxy.name);
            ArtifactoryPluginUtils.addProperty(stringBuilder, ClientProperties.PROP_PROXY_HOST,
                    String.valueOf(proxy.port));
            ArtifactoryPluginUtils
                    .addProperty(stringBuilder, ClientProperties.PROP_PROXY_USERNAME, proxy.getUserName());
            ArtifactoryPluginUtils
                    .addProperty(stringBuilder, ClientProperties.PROP_PROXY_PASSWORD, proxy.getPassword());
        }
        IncludesExcludes deploymentPatterns = gradleConfigurator.getArtifactDeploymentPatterns();
        if (deploymentPatterns != null) {
            String includePatterns = deploymentPatterns.getIncludePatterns();
            if (StringUtils.isNotBlank(includePatterns)) {
                ArtifactoryPluginUtils.addProperty(stringBuilder,
                        ClientProperties.PROP_PUBLISH_ARTIFACT_INCLUDE_PATTERNS,
                        includePatterns);
            }
            String excludePatterns = deploymentPatterns.getExcludePatterns();
            if (StringUtils.isNotBlank(excludePatterns)) {
                ArtifactoryPluginUtils
                        .addProperty(stringBuilder, ClientProperties.PROP_PUBLISH_ARTIFACT_EXCLUDE_PATTERNS,
                                excludePatterns);
            }
        }
        if (gradleConfigurator.isDiscardOldBuilds()) {
            LogRotator rotator = build.getProject().getLogRotator();
            if (rotator != null) {
                if (rotator.getNumToKeep() > -1) {
                    ArtifactoryPluginUtils.addProperty(stringBuilder, BuildInfoProperties.PROP_BUILD_RETENTION_DAYS,
                            String.valueOf(String.valueOf(rotator.getNumToKeep())));
                }
                if (rotator.getDaysToKeep() > -1) {
                    ArtifactoryPluginUtils
                            .addProperty(stringBuilder, BuildInfoProperties.PROP_BUILD_RETENTION_MINIMUM_DATE,
                                    String.valueOf(String.valueOf(rotator.getDaysToKeep())));
                }
            }
        }

        String principal = ActionableHelper.getHudsonPrincipal(build);
        ArtifactoryPluginUtils.addProperty(stringBuilder, BuildInfoProperties.PROP_PRINCIPAL, principal);
        String buildUrl = ActionableHelper.getBuildUrl(build);
        if (StringUtils.isNotBlank(buildUrl)) {
            ArtifactoryPluginUtils.addProperty(stringBuilder, BuildInfoProperties.PROP_BUILD_URL, buildUrl);
        }
        String svnRevision = envVars.get("SVN_REVISION");
        if (StringUtils.isNotBlank(svnRevision)) {
            ArtifactoryPluginUtils.addProperty(stringBuilder, BuildInfoProperties.PROP_VCS_REVISION, svnRevision);
        }
        ArtifactoryPluginUtils.addProperty(stringBuilder, BuildInfoProperties.PROP_AGENT_NAME, "Jenkins");
        ArtifactoryPluginUtils
                .addProperty(stringBuilder, BuildInfoProperties.PROP_AGENT_VERSION, build.getHudsonVersion());
        Cause.UpstreamCause parent = ActionableHelper.getUpstreamCause(build);
        if (parent != null) {
            ArtifactoryPluginUtils.addProperty(stringBuilder, BuildInfoProperties.PROP_PARENT_BUILD_NAME,
                    parent.getUpstreamProject());
            ArtifactoryPluginUtils.addProperty(stringBuilder, BuildInfoProperties.PROP_PARENT_BUILD_NUMBER,
                    parent.getUpstreamBuild() + "");
        }

        // Write all the deploy (matrix params) properties.
        Map<String, String> filteredMatrixParams = Maps.filterKeys(envVars, new Predicate<String>() {
            public boolean apply(String input) {
                return input.startsWith(ClientProperties.PROP_DEPLOY_PARAM_PROP_PREFIX);
            }
        });
        for (Map.Entry<String, String> entry : filteredMatrixParams.entrySet()) {
            ArtifactoryPluginUtils.addProperty(stringBuilder, entry.getKey(), entry.getValue());
        }

        // add EnvVars

        //Add only the hudson specific environment variables
        MapDifference<String, String> difference = Maps.difference(envVars, System.getenv());
        Map<String, String> filteredEnvVars = difference.entriesOnlyOnLeft();
        for (Map.Entry<String, String> entry : filteredEnvVars.entrySet()) {
            ArtifactoryPluginUtils
                    .addProperty(stringBuilder, BuildInfoProperties.BUILD_INFO_ENVIRONMENT_PREFIX + entry.getKey(),
                            entry.getValue());
        }
        ArtifactoryPluginUtils.addProperty(stringBuilder, BuildInfoConfigProperties.PROP_INCLUDE_ENV_VARS,
                String.valueOf(gradleConfigurator.includeEnvVars));
        // add build variables
        Map<String, String> buildVariables = build.getBuildVariables();
        for (Map.Entry<String, String> entry : buildVariables.entrySet()) {
            ArtifactoryPluginUtils
                    .addProperty(stringBuilder, BuildInfoProperties.BUILD_INFO_ENVIRONMENT_PREFIX + entry.getKey(),
                            entry.getValue());
        }
        return stringBuilder.toString();
    }

    /**
     * Generate the init script from the Artifactory URL.
     *
     * @return The generated script.
     */
    public String generateInitScript() throws URISyntaxException, IOException, InterruptedException {
        StringBuilder initScript = new StringBuilder();
        InputStream templateStream = getClass().getResourceAsStream("/initscripttemplate.gradle");
        String templateAsString = IOUtils.toString(templateStream, Charsets.UTF_8.name());
        String str = templateAsString.replace("${allBuildInfoProperties}", addProperties());

        File localGradleExtractorJar = Which.jarFile(getClass().getResource("/initscripttemplate.gradle"));
        FilePath dependencyDir = PluginDependencyHelper.getActualDependencyDirectory(build, localGradleExtractorJar);

        String absoluteDependencyDirPath = dependencyDir.getRemote();
        absoluteDependencyDirPath = absoluteDependencyDirPath.replace("\\", "/");
        str = str.replace("${pluginLibDir}", absoluteDependencyDirPath);
        initScript.append(str);
        return initScript.toString();
    }

    private ServerDetails getServerDetails() {
        return gradleConfigurator.getDetails();
    }

    private ArtifactoryServer getArtifactoryServer() {
        return gradleConfigurator.getArtifactoryServer();
    }
}
