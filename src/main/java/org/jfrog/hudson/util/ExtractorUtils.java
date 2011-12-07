/*
 * Copyright (C) 2011 JFrog Ltd.
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

package org.jfrog.hudson.util;

import com.google.common.collect.Lists;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import com.google.common.io.Closeables;
import hudson.FilePath;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Computer;
import hudson.model.Run;
import hudson.slaves.SlaveComputer;
import hudson.tasks.LogRotator;
import org.apache.commons.lang.StringUtils;
import org.jfrog.build.api.BuildInfoConfigProperties;
import org.jfrog.build.api.BuildInfoFields;
import org.jfrog.build.api.util.NullLog;
import org.jfrog.build.client.ArtifactoryClientConfiguration;
import org.jfrog.build.client.ClientProperties;
import org.jfrog.hudson.ArtifactoryServer;
import org.jfrog.hudson.action.ActionableHelper;
import org.jfrog.hudson.release.ReleaseAction;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * @author Tomer Cohen
 */
public class ExtractorUtils {

    /**
     * Flag to indicate whether an external extractor was used, and the work doesn't need to be done from inside
     * Jenkins.
     */
    public static final String EXTRACTOR_USED = "extractor.used";

    private ExtractorUtils() {
        // utility class
        throw new IllegalAccessError();
    }

    /**
     * Get the VCS revision from the Jenkins build environment. The search will first be for an "SVN_REVISION" in the
     * environment. If it is not found then a search will be for a "GIT_COMMIT".
     *
     * @param env Th Jenkins build environment.
     * @return The subversion revision if found, or the git revision if found.
     */
    public static String getVcsRevision(Map<String, String> env) {
        String revision = env.get("SVN_REVISION");
        if (StringUtils.isBlank(revision)) {
            revision = env.get("GIT_COMMIT");
        }
        return revision;
    }

    /**
     * Add build info properties that will be read by an external extractor. All properties are then saved into a {@code
     * buildinfo.properties} into a temporary location. The location is then put into an environment variable {@link
     * BuildInfoConfigProperties#PROP_PROPS_FILE} for the extractor to read.
     *
     * @param env                       A map of the environment variables that are to be persisted into the
     *                                  buildinfo.properties file. NOTE: nothing should be added to the env in this
     *                                  method
     * @param build                     The build from which to get build/project related information from (e.g build
     *                                  name and build number).
     * @param selectedArtifactoryServer The Artifactory server that is to be used during the build for resolution/
     *                                  deployment
     * @param publisherContext          A context for publisher settings
     * @param resolverContext           A context for resolver settings
     */
    public static ArtifactoryClientConfiguration addBuilderInfoArguments(Map<String, String> env, AbstractBuild build,
            PublisherContext publisherContext, ResolverContext resolverContext)
            throws IOException, InterruptedException {
        ArtifactoryClientConfiguration configuration = new ArtifactoryClientConfiguration(new NullLog());
        addBuildRootIfNeeded(build, configuration);

        if (publisherContext != null) {
            setPublisherInfo(env, build, publisherContext, configuration);
        }

        if (resolverContext != null) {
            setResolverInfo(configuration, resolverContext);
        }

        addEnvVars(env, build, configuration);
        persistConfiguration(build, configuration, env);
        return configuration;
    }

    private static void setResolverInfo(ArtifactoryClientConfiguration configuration, ResolverContext context) {
        configuration.setTimeout(context.getServer().getTimeout());
        configuration.resolver.setContextUrl(context.getServer().getUrl());
        configuration.resolver.setRepoKey(context.getServerDetails().downloadRepositoryKey);
        configuration.resolver.setUsername(context.getCredentials().getUsername());
        configuration.resolver.setPassword(context.getCredentials().getPassword());
        //env.putAll(configuration.getAllProperties());
    }

    /**
     * Set all the parameters relevant for publishing artifacts and build info
     */
    private static void setPublisherInfo(Map<String, String> env, AbstractBuild build,
            PublisherContext context, ArtifactoryClientConfiguration configuration) {
        configuration.setActivateRecorder(Boolean.TRUE);

        String buildName = build.getProject().getDisplayName();
        configuration.info.setBuildName(buildName);
        configuration.publisher.addMatrixParam("build.name", buildName);
        String buildNumber = build.getNumber() + "";
        configuration.info.setBuildNumber(buildNumber);
        configuration.publisher.addMatrixParam("build.number", buildNumber);

        Date buildStartDate = build.getTimestamp().getTime();
        configuration.info.setBuildStarted(buildStartDate.getTime());
        configuration.info.setBuildTimestamp(String.valueOf(buildStartDate.getTime()));
        configuration.publisher.addMatrixParam("build.timestamp", String.valueOf(buildStartDate.getTime()));

        String vcsRevision = getVcsRevision(env);
        if (StringUtils.isNotBlank(vcsRevision)) {
            configuration.info.setVcsRevision(vcsRevision);
            configuration.publisher.addMatrixParam(BuildInfoFields.VCS_REVISION, vcsRevision);
        }

        if (StringUtils.isNotBlank(context.getArtifactsPattern())) {
            configuration.publisher.setIvyArtifactPattern(context.getArtifactsPattern());
        }
        if (StringUtils.isNotBlank(context.getIvyPattern())) {
            configuration.publisher.setIvyPattern(context.getIvyPattern());
        }
        configuration.publisher.setM2Compatible(context.isMaven2Compatible());
        String buildUrl = ActionableHelper.getBuildUrl(build);
        if (StringUtils.isNotBlank(buildUrl)) {
            configuration.info.setBuildUrl(buildUrl);
        }

        String userName = "unknown";
        Cause.UpstreamCause parent = ActionableHelper.getUpstreamCause(build);
        if (parent != null) {
            String parentProject = parent.getUpstreamProject();
            configuration.info.setParentBuildName(parentProject);
            configuration.publisher.addMatrixParam(BuildInfoFields.BUILD_PARENT_NAME, parentProject);
            String parentBuildNumber = parent.getUpstreamBuild() + "";
            configuration.info.setParentBuildNumber(parentBuildNumber);
            configuration.publisher.addMatrixParam(BuildInfoFields.BUILD_PARENT_NUMBER, parentBuildNumber);
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
        configuration.info.setPrincipal(userName);
        configuration.info.setAgentName("Jenkins");
        configuration.info.setAgentVersion(build.getHudsonVersion());
        ArtifactoryServer artifactoryServer = context.getArtifactoryServer();
        Credentials preferredDeployer =
                CredentialResolver.getPreferredDeployer(context.getDeployerOverrider(), artifactoryServer);
        if (StringUtils.isNotBlank(preferredDeployer.getUsername())) {
            configuration.publisher.setUsername(preferredDeployer.getUsername());
            configuration.publisher.setPassword(preferredDeployer.getPassword());
        }
        configuration.setTimeout(artifactoryServer.getTimeout());
        configuration.publisher.setContextUrl(artifactoryServer.getUrl());
        configuration.publisher.setRepoKey(context.getServerDetails().repositoryKey);
        configuration.publisher.setSnapshotRepoKey(context.getServerDetails().snapshotsRepositoryKey);

        // note: we set the context url for both the publisher and resolver for backward compatibility
        configuration.resolver.setContextUrl(artifactoryServer.getUrl());
        if (StringUtils.isNotBlank(context.getServerDetails().downloadRepositoryKey)) {
            configuration.resolver.setRepoKey(context.getServerDetails().downloadRepositoryKey);
        }

        configuration.info.licenseControl.setRunChecks(context.isRunChecks());
        configuration.info.licenseControl.setIncludePublishedArtifacts(context.isIncludePublishArtifacts());
        configuration.info.licenseControl.setAutoDiscover(context.isLicenseAutoDiscovery());
        if (context.isRunChecks()) {
            if (StringUtils.isNotBlank(context.getViolationRecipients())) {
                configuration.info.licenseControl.setViolationRecipients(context.getViolationRecipients());
            }
            if (StringUtils.isNotBlank(context.getScopes())) {
                configuration.info.licenseControl.setScopes(context.getScopes());
            }
        }
        if (context.isDiscardOldBuilds()) {
            LogRotator rotator = build.getProject().getLogRotator();
            if (rotator != null) {
                if (rotator.getNumToKeep() > -1) {
                    configuration.info.setBuildRetentionDays(rotator.getNumToKeep());
                }
                if (rotator.getDaysToKeep() > -1) {
                    configuration.info.setBuildRetentionMinimumDate(String.valueOf(rotator.getDaysToKeep()));
                }
                configuration.info.setDeleteBuildArtifacts(context.isDiscardBuildArtifacts());
            }
            configuration.info.setBuildNumbersNotToDelete(getBuildNumbersNotToBeDeletedAsString(build));
        }
        configuration.publisher.setPublishArtifacts(context.isDeployArtifacts());
        configuration.publisher.setEvenUnstable(context.isEvenIfUnstable());
        configuration.publisher.setIvy(context.isDeployIvy());
        configuration.publisher.setMaven(context.isDeployMaven());
        IncludesExcludes deploymentPatterns = context.getIncludesExcludes();
        if (deploymentPatterns != null) {
            String includePatterns = deploymentPatterns.getIncludePatterns();
            if (StringUtils.isNotBlank(includePatterns)) {
                configuration.publisher.setIncludePatterns(includePatterns);
            }
            String excludePatterns = deploymentPatterns.getExcludePatterns();
            if (StringUtils.isNotBlank(excludePatterns)) {
                configuration.publisher.setExcludePatterns(excludePatterns);
            }
        }
        ReleaseAction releaseAction = ActionableHelper.getLatestAction(build, ReleaseAction.class);
        if (releaseAction != null) {
            configuration.info.setReleaseEnabled(true);
            String comment = releaseAction.getStagingComment();
            if (StringUtils.isNotBlank(comment)) {
                configuration.info.setReleaseComment(comment);
            }
        }
        configuration.publisher.setPublishBuildInfo(!context.isSkipBuildInfoDeploy());
        configuration.setIncludeEnvVars(context.isIncludeEnvVars());
        addMatrixParams(context, configuration.publisher, env);
    }

    /**
     * Get the list of build numbers that are to be kept forever.
     */
    public static List<String> getBuildNumbersNotToBeDeleted(AbstractBuild build) {
        List<String> notToDelete = Lists.newArrayList();
        List<? extends Run<?, ?>> builds = build.getProject().getBuilds();
        for (Run<?, ?> run : builds) {
            if (run.isKeepLog()) {
                notToDelete.add(String.valueOf(run.getNumber()));
            }
        }
        return notToDelete;
    }

    private static String getBuildNumbersNotToBeDeletedAsString(AbstractBuild build) {
        StringBuilder builder = new StringBuilder();
        List<String> notToBeDeleted = getBuildNumbersNotToBeDeleted(build);
        for (String notToDelete : notToBeDeleted) {
            builder.append(notToDelete).append(",");
        }
        return builder.toString();
    }

    public static void addBuildRootIfNeeded(AbstractBuild build, ArtifactoryClientConfiguration configuration) {
        AbstractBuild<?, ?> rootBuild = BuildUniqueIdentifierHelper.getRootBuild(build);
        if (rootBuild != null) {
            String identifier = BuildUniqueIdentifierHelper.getUpstreamIdentifier(rootBuild);
            configuration.info.setBuildRoot(identifier);
        }
    }

    public static void persistConfiguration(AbstractBuild build, ArtifactoryClientConfiguration configuration,
            Map<String, String> env) throws IOException, InterruptedException {
        FilePath propertiesFile = build.getWorkspace().createTextTempFile("buildInfo", ".properties", "", false);
        configuration.setPropertiesFile(propertiesFile.getRemote());
        env.put("BUILDINFO_PROPFILE", propertiesFile.getRemote());
        env.put(BuildInfoConfigProperties.PROP_PROPS_FILE, propertiesFile.getRemote());
        if (!(Computer.currentComputer() instanceof SlaveComputer)) {
            configuration.persistToPropertiesFile();
        } else {
            try {
                Properties properties = new Properties();
                properties.putAll(configuration.getAllRootConfig());
                properties.putAll(configuration.getAllProperties());
                File tempFile = File.createTempFile("buildInfo", ".properties");
                FileOutputStream stream = new FileOutputStream(tempFile);
                try {
                    properties.store(stream, "");
                } finally {
                    Closeables.closeQuietly(stream);
                }
                propertiesFile.copyFrom(tempFile.toURI().toURL());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void addMatrixParams(PublisherContext context,
            ArtifactoryClientConfiguration.PublisherHandler publisher,
            Map<String, String> env) {
        String matrixParams = context.getMatrixParams();
        if (StringUtils.isBlank(matrixParams)) {
            return;
        }
        String[] keyValuePairs = StringUtils.split(matrixParams, "; ");
        if (keyValuePairs == null) {
            return;
        }
        for (String keyValuePair : keyValuePairs) {
            String[] split = StringUtils.split(keyValuePair, "=");
            if (split.length == 2) {
                String value = Util.replaceMacro(split[1], env);
                publisher.addMatrixParam(split[0], value);
            }
        }
    }

    private static void addEnvVars(Map<String, String> env, AbstractBuild<?, ?> build,
            ArtifactoryClientConfiguration configuration) {
        // Write all the deploy (matrix params) properties.
        configuration.fillFromProperties(env);
        //Add only the jenkins specific environment variables
        MapDifference<String, String> envDifference = Maps.difference(env, System.getenv());
        Map<String, String> filteredEnvDifference = envDifference.entriesOnlyOnLeft();
        configuration.info.addBuildVariables(filteredEnvDifference);

        // add build variables
        Map<String, String> buildVariables = build.getBuildVariables();
        configuration.fillFromProperties(buildVariables);
        for (Map.Entry<String, String> entry : buildVariables.entrySet()) {
            if (entry.getKey().startsWith(ClientProperties.PROP_DEPLOY_PARAM_PROP_PREFIX)) {
                configuration.publisher.addMatrixParam(entry.getKey(), entry.getValue());
            }
        }
        Map<String, String> filteredBuildVars = Maps.newHashMap();

        MapDifference<String, String> buildVarDifference = Maps.difference(buildVariables, filteredBuildVars);
        Map<String, String> filteredBuildVarDifferences = buildVarDifference.entriesOnlyOnLeft();

        configuration.info.addBuildVariables(filteredBuildVarDifferences);
    }
}
