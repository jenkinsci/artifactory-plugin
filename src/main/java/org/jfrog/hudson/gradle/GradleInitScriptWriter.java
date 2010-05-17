/*
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2010 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.jfrog.hudson.gradle;

import com.google.common.base.Predicate;
import com.google.common.collect.Maps;
import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.Cause;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.jfrog.build.api.BuildInfoConfigProperties;
import org.jfrog.build.api.BuildInfoProperties;
import org.jfrog.build.client.ClientProperties;
import org.jfrog.hudson.ArtifactoryServer;
import org.jfrog.hudson.ServerDetails;
import org.jfrog.hudson.util.ActionableHelper;

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

    private EnvVars envVars;
    private AbstractBuild build;
    private static final String NEW_LINE = "\n";
    private static final String QUOTE = "\"";
    private static String scriptRepoPath =
            "org/jfrog/buildinfo/build-info-extractor-gradle/1.0-SNAPSHOT/artifactoryinitplugin-1.0-SNAPSHOT.gradle";
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
        addProperty(stringBuilder, ClientProperties.PROP_PUBLISH_IVY, Boolean.toString(gradleConfigurator.deployIvy));
        addProperty(stringBuilder, ClientProperties.PROP_PUBLISH_MAVEN,
                Boolean.toString(gradleConfigurator.deployMaven));
        addProperty(stringBuilder, ClientProperties.PROP_PUBLISH_ARTIFACT,
                Boolean.toString(gradleConfigurator.isDeployArtifacts()));
        addProperty(stringBuilder, BuildInfoProperties.PROP_BUILD_NAME, build.getProject().getName());
        addProperty(stringBuilder, BuildInfoProperties.PROP_BUILD_NUMBER, build.getNumber() + "");
        String buildUrl = envVars.get("BUILD_URL");
        if (StringUtils.isNotBlank(buildUrl)) {
            addProperty(stringBuilder, BuildInfoProperties.PROP_BUILD_URL, buildUrl);
        }
        String svnRevision = envVars.get("SVN_REVISION");
        if (StringUtils.isNotBlank(svnRevision)) {
            addProperty(stringBuilder, BuildInfoProperties.PROP_VCS_REVISION, svnRevision);
        }
        addProperty(stringBuilder, BuildInfoProperties.PROP_BUILD_AGENT, "Hudson/" + build.getHudsonVersion());
        Cause.UpstreamCause parent = ActionableHelper.getUpstreamCause(build);
        if (parent != null) {
            addProperty(stringBuilder, BuildInfoProperties.PROP_PARENT_BUILD_NAME, parent.getUpstreamProject());
            addProperty(stringBuilder, BuildInfoProperties.PROP_PARENT_BUILD_NUMBER, parent.getUpstreamBuild() + "");
        }
        // Write all the buildInfo properties.
        Map<String, String> filteredBuildInfoKeys = Maps.filterKeys(envVars, new Predicate<String>() {
            public boolean apply(String input) {
                return input.startsWith(BuildInfoProperties.BUILD_INFO_PROP_PREFIX);
            }
        });
        for (Map.Entry<String, String> entry : filteredBuildInfoKeys.entrySet()) {
            addProperty(stringBuilder, entry.getKey(), entry.getValue());
        }
        // Write all the deploy (matrix params) properties.
        Map<String, String> filteredMatrixParams = Maps.filterKeys(envVars, new Predicate<String>() {
            public boolean apply(String input) {
                return input.startsWith(BuildInfoConfigProperties.BUILD_INFO_DEPLOY_PROP_PREFIX);
            }
        });
        for (Map.Entry<String, String> entry : filteredMatrixParams.entrySet()) {
            addProperty(stringBuilder, entry.getKey(), entry.getValue());
        }
        return stringBuilder.toString();
    }

    private void addProperty(StringBuilder stringBuilder, String key, String value) {
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
        String templateAsString = FileUtils.readFileToString(template);
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
