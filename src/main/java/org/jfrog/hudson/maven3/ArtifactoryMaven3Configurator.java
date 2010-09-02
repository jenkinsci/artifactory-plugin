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
import com.thoughtworks.xstream.annotations.XStreamAlias;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.FreeStyleProject;
import hudson.model.Hudson;
import hudson.model.Result;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.Scrambler;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.jfrog.build.api.Build;
import org.jfrog.build.api.BuildInfoConfigProperties;
import org.jfrog.build.api.BuildInfoProperties;
import org.jfrog.build.client.ClientProperties;
import org.jfrog.build.extractor.maven.BuildInfoRecorder;
import org.jfrog.hudson.ArtifactoryBuilder;
import org.jfrog.hudson.ArtifactoryServer;
import org.jfrog.hudson.BuildInfoResultAction;
import org.jfrog.hudson.ServerDetails;
import org.jfrog.hudson.action.ActionableHelper;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * @author Noam Y. Tenne
 */
@XStreamAlias("artifactory-maven3-config")
public class ArtifactoryMaven3Configurator extends BuildWrapper {

    private final ServerDetails details;
    private final String username;
    private final String scrambledPassword;
    private final boolean deployArtifacts;
    private final boolean includeEnvVars;

    @DataBoundConstructor
    public ArtifactoryMaven3Configurator(ServerDetails details, String username, String password,
            boolean deployArtifacts, boolean includeEnvVars) {
        this.details = details;
        this.username = username;
        this.scrambledPassword = Scrambler.scramble(password);
        this.deployArtifacts = deployArtifacts;
        this.includeEnvVars = includeEnvVars;
    }

    public ServerDetails getDetails() {
        return details;
    }

    public String getRepositoryKey() {
        return details != null ? details.repositoryKey : null;
    }

    public String getArtifactoryName() {
        return details != null ? details.artifactoryName : null;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return Scrambler.descramble(scrambledPassword);
    }

    public boolean isDeployArtifacts() {
        return deployArtifacts;
    }

    public boolean isIncludeEnvVars() {
        return includeEnvVars;
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
                    addBuilderInfoArguments(env, build, listener, artifactoryServer);
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
                if (result == null) {
                    return false;
                }
                if (result.isBetterOrEqualTo(Result.SUCCESS)) {
                    build.getActions().add(new BuildInfoResultAction(getArtifactoryName(), build));
                    return true;
                }
                return false;
            }
        };
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    private void addBuilderInfoArguments(Map<String, String> env, AbstractBuild build, BuildListener listener,
            ArtifactoryServer selectedArtifactoryServer) throws IOException, InterruptedException {

        Properties props = new Properties();

        props.put(BuildInfoRecorder.ACTIVATE_RECORDER, Boolean.TRUE.toString());

        String buildName = build.getProject().getDisplayName();
        props.put(BuildInfoProperties.PROP_BUILD_NAME, buildName);
        props.put(ClientProperties.PROP_DEPLOY_PARAM_PROP_PREFIX + "build.name", buildName);

        String buildNumber = build.getNumber() + "";
        props.put(BuildInfoProperties.PROP_BUILD_NUMBER, buildNumber);
        props.put(ClientProperties.PROP_DEPLOY_PARAM_PROP_PREFIX + "build.number", buildName);

        props.put(BuildInfoProperties.PROP_BUILD_STARTED,
                new SimpleDateFormat(Build.STARTED_FORMAT).format(build.getTimestamp().getTime()));

        String vcsRevision = env.get("SVN_REVISION");
        if (StringUtils.isNotBlank(vcsRevision)) {
            props.put(BuildInfoProperties.PROP_VCS_REVISION, vcsRevision);
            props.put(ClientProperties.PROP_DEPLOY_PARAM_PROP_PREFIX +
                    BuildInfoProperties.PROP_VCS_REVISION, vcsRevision);
        }

        String buildUrl = Hudson.getInstance().getRootUrl() + build.getUrl();
        props.put(BuildInfoProperties.PROP_BUILD_URL, buildUrl);

        String userName = "unknown";
        Cause.UpstreamCause parent = ActionableHelper.getUpstreamCause(build);
        if (parent != null) {
            String parentProject = parent.getUpstreamProject();
            props.put(BuildInfoProperties.PROP_PARENT_BUILD_NAME, parentProject);
            props.put(ClientProperties.PROP_DEPLOY_PARAM_PROP_PREFIX +
                    BuildInfoProperties.PROP_PARENT_BUILD_NAME, parentProject);

            String parentBuildName = parent.getUpstreamBuild() + "";
            props.put(BuildInfoProperties.PROP_PARENT_BUILD_NUMBER, parentBuildName);
            props.put(ClientProperties.PROP_DEPLOY_PARAM_PROP_PREFIX +
                    BuildInfoProperties.PROP_PARENT_BUILD_NUMBER, parentBuildName);
            userName = "auto";
        }

        CauseAction action = ActionableHelper.getLatestAction(build, CauseAction.class);
        if (action != null) {
            for (Cause cause : action.getCauses()) {
                if (cause instanceof Cause.UserCause) {
                    userName = ((Cause.UserCause) cause).getUserName();
                }
            }
        }

        props.put(BuildInfoProperties.PROP_PRINCIPAL, userName);

        props.put(BuildInfoProperties.PROP_AGENT_NAME, "Hudson");
        props.put(BuildInfoProperties.PROP_AGENT_VERSION, build.getHudsonVersion());

        props.put(ClientProperties.PROP_CONTEXT_URL, selectedArtifactoryServer.getUrl());
        props.put(ClientProperties.PROP_TIMEOUT, Integer.toString(selectedArtifactoryServer.getTimeout()));
        props.put(ClientProperties.PROP_PUBLISH_REPOKEY, getDetails().repositoryKey);

        String deployerUsername = getUsername();
        if (StringUtils.isNotBlank(deployerUsername)) {
            props.put(ClientProperties.PROP_PUBLISH_USERNAME, deployerUsername);
            props.put(ClientProperties.PROP_PUBLISH_PASSWORD, getPassword());
        }

        props.put(ClientProperties.PROP_PUBLISH_ARTIFACT, Boolean.toString(isDeployArtifacts()));
        props.put(ClientProperties.PROP_PUBLISH_BUILD_INFO, Boolean.TRUE.toString());
        props.put(BuildInfoConfigProperties.PROP_INCLUDE_ENV_VARS, Boolean.toString(isIncludeEnvVars()));
        addEnvVars(env, build, props);


        File tempPropsFile = File.createTempFile("buildInfo", "properties");
        FileOutputStream fileOutputStream = new FileOutputStream(tempPropsFile);
        try {
            props.store(fileOutputStream, null);
        } finally {
            IOUtils.closeQuietly(fileOutputStream);
        }

        env.put(BuildInfoConfigProperties.PROP_PROPS_FILE, tempPropsFile.getCanonicalPath());
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
            return "Maven3-Artifactory Integration";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            req.bindParameters(this, "maven3");
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
