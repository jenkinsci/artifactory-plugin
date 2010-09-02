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
import com.google.common.io.Files;
import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.Cause;
import org.apache.commons.lang.StringUtils;
import org.jfrog.build.api.BuildInfoConfigProperties;
import org.jfrog.build.api.BuildInfoProperties;
import org.jfrog.build.client.ClientGradleProperties;
import org.jfrog.build.client.ClientIvyProperties;
import org.jfrog.build.client.ClientProperties;
import org.jfrog.hudson.ArtifactoryServer;
import org.jfrog.hudson.ServerDetails;
import org.jfrog.hudson.action.ActionableHelper;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Map;


/**
 * Class to generate a Gradle initialization script
 *
 * @author Tomer Cohen
 */
public class GradleInitScriptWriter {
    private static final String NEW_LINE = "\n";
    private static final String QUOTE = "'";

    private EnvVars envVars;
    private AbstractBuild build;
    private String scriptRepoPath =
            "org/jfrog/buildinfo/build-info-extractor-gradle/1.0.1/artifactoryinitplugin-1.0.1.gradle";
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
        String value = getArtifactoryServer().getUrl();
        addProperty(stringBuilder, key, value);
        addProperty(stringBuilder, ClientProperties.PROP_RESOLVE_REPOKEY, getServerDetails().downloadRepositoryKey);
        addProperty(stringBuilder, ClientProperties.PROP_PUBLISH_REPOKEY, getServerDetails().repositoryKey);
        addProperty(stringBuilder, ClientProperties.PROP_PUBLISH_USERNAME, gradleConfigurator.getUsername());
        addProperty(stringBuilder, ClientProperties.PROP_PUBLISH_PASSWORD, gradleConfigurator.getPassword());
        addProperty(stringBuilder, ClientIvyProperties.PROP_PUBLISH_IVY,
                Boolean.toString(gradleConfigurator.deployIvy));
        addProperty(stringBuilder, ClientGradleProperties.PROP_PUBLISH_MAVEN,
                Boolean.toString(gradleConfigurator.deployMaven));
        addProperty(stringBuilder, ClientProperties.PROP_PUBLISH_ARTIFACT,
                Boolean.toString(gradleConfigurator.isDeployArtifacts()));
        addProperty(stringBuilder, ClientProperties.PROP_PUBLISH_BUILD_INFO,
                Boolean.toString(gradleConfigurator.deployBuildInfo));
        addProperty(stringBuilder, BuildInfoProperties.PROP_BUILD_NAME, build.getProject().getName());
        addProperty(stringBuilder, BuildInfoProperties.PROP_BUILD_NUMBER, build.getNumber() + "");
        String principal = ActionableHelper.getHudsonPrincipal(build);
        addProperty(stringBuilder, BuildInfoProperties.PROP_PRINCIPAL, principal);
        String buildUrl = envVars.get("BUILD_URL");
        if (StringUtils.isNotBlank(buildUrl)) {
            addProperty(stringBuilder, BuildInfoProperties.PROP_BUILD_URL, buildUrl);
        }
        String svnRevision = envVars.get("SVN_REVISION");
        if (StringUtils.isNotBlank(svnRevision)) {
            addProperty(stringBuilder, BuildInfoProperties.PROP_VCS_REVISION, svnRevision);
        }
        addProperty(stringBuilder, BuildInfoProperties.PROP_BUILD_AGENT_NAME, "Hudson");
        addProperty(stringBuilder, BuildInfoProperties.PROP_BUILD_AGENT_VERSION, build.getHudsonVersion());
        Cause.UpstreamCause parent = ActionableHelper.getUpstreamCause(build);
        if (parent != null) {
            addProperty(stringBuilder, BuildInfoProperties.PROP_PARENT_BUILD_NAME, parent.getUpstreamProject());
            addProperty(stringBuilder, BuildInfoProperties.PROP_PARENT_BUILD_NUMBER, parent.getUpstreamBuild() + "");
        }

        // Write all the deploy (matrix params) properties.
        Map<String, String> filteredMatrixParams = Maps.filterKeys(envVars, new Predicate<String>() {
            public boolean apply(String input) {
                return input.startsWith(ClientProperties.PROP_DEPLOY_PARAM_PROP_PREFIX);
            }
        });
        for (Map.Entry<String, String> entry : filteredMatrixParams.entrySet()) {
            addProperty(stringBuilder, entry.getKey(), entry.getValue());
        }

        // add EnvVars

        //Add only the hudson specific environment variables
        MapDifference<String, String> difference = Maps.difference(envVars, System.getenv());
        Map<String, String> filteredEnvVars = difference.entriesOnlyOnLeft();
        for (Map.Entry<String, String> entry : filteredEnvVars.entrySet()) {
            addProperty(stringBuilder, BuildInfoProperties.BUILD_INFO_ENVIRONMENT_PREFIX + entry.getKey(),
                    entry.getValue());
        }
        addProperty(stringBuilder, BuildInfoConfigProperties.PROP_INCLUDE_ENV_VARS,
                String.valueOf(gradleConfigurator.includeEnvVars));
        // add build variables
        Map<String, String> buildVariables = build.getBuildVariables();
        for (Map.Entry<String, String> entry : buildVariables.entrySet()) {
            addProperty(stringBuilder, BuildInfoProperties.BUILD_INFO_ENVIRONMENT_PREFIX + entry.getKey(),
                    entry.getValue());
        }

        return stringBuilder.toString();
    }

    private void addProperty(StringBuilder stringBuilder, String key, String value) {
        key = key.replace("\\", "\\\\");
        value = value.replace("\\", "\\\\");
        value = value.replace('"', ' ');
        stringBuilder.append(QUOTE).append(key).append(QUOTE).append(":").append(QUOTE).append(value).append(QUOTE)
                .append(",").append(NEW_LINE);
    }

    /**
     * Generate the init script from the Artifactory URL.
     *
     * @return The generated script.
     */
    public String generateInitScript() throws URISyntaxException, IOException {
        StringBuilder initScript = new StringBuilder();
        File template = new File(getClass().getResource("/initscriptemplate.gradle").toURI());
        String templateAsString = Files.toString(template, Charsets.UTF_8);
        if (StringUtils.isNotBlank(gradleConfigurator.remotePluginLocation)) {
            scriptRepoPath = gradleConfigurator.remotePluginLocation;
        }
        String str = templateAsString.replace("${artifactoryPluginDownloadUrl}",
                getArtifactoryServer().getUrl() + "/" + getServerDetails().downloadRepositoryKey + "/" +
                        scriptRepoPath);
        str = str.replace("${allHudsonProperties}", addProperties());
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
