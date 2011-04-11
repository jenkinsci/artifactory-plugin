package org.jfrog.hudson.util;

import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.Run;
import hudson.remoting.VirtualChannel;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.slaves.SlaveComputer;
import hudson.tasks.LogRotator;
import hudson.tasks.Maven;
import hudson.tools.ToolLocationNodeProperty;
import hudson.util.DescribableList;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.jfrog.build.api.BuildInfoConfigProperties;
import org.jfrog.build.api.BuildInfoFields;
import org.jfrog.build.api.util.NullLog;
import org.jfrog.build.client.ArtifactoryClientConfiguration;
import org.jfrog.hudson.ArtifactoryServer;
import org.jfrog.hudson.action.ActionableHelper;
import org.jfrog.hudson.release.ReleaseAction;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @author Tomer Cohen
 */
public class ExtractorUtils {
    private ExtractorUtils() {
        // utility class
        throw new IllegalAccessError();
    }

    /**
     * Get the Maven home directory from where to execute Maven from. This is primarily used when running an external
     * Maven invocation outside of Jenkins, can will return a slave Maven home if Maven is on slave according to the
     * maven installation.
     *
     * @return The maven home
     */
    public static FilePath getMavenHomeDir(AbstractBuild<?, ?> build, BuildListener listener, Map<String, String> env,
            Maven.MavenInstallation mavenInstallation) {
        Computer computer = Computer.currentComputer();
        VirtualChannel virtualChannel = computer.getChannel();

        String mavenHome = null;

        //Check for a node defined tool if we are on a slave
        if (computer instanceof SlaveComputer) {
            mavenHome = getNodeDefinedMavenHome(build);
        }

        //Either we are on the master or that no node tool was defined
        if (StringUtils.isBlank(mavenHome)) {
            mavenHome = getJobDefinedMavenInstallation(listener, mavenInstallation);
        }

        //Try to find the home via the env vars
        if (StringUtils.isBlank(mavenHome)) {
            mavenHome = getEnvDefinedMavenHome(env);
        }
        return new FilePath(virtualChannel, mavenHome);
    }

    private static String getJobDefinedMavenInstallation(BuildListener listener,
            Maven.MavenInstallation mavenInstallation) {
        if (mavenInstallation == null) {
            listener.error("Maven version is not configured for this project. Can't determine which Maven to run");
            throw new Run.RunnerAbortedException();
        }
        String mvnHome = mavenInstallation.getHome();
        if (mvnHome == null) {
            listener.error("Maven '%s' doesn't have its home set", mavenInstallation.getName());
            throw new Run.RunnerAbortedException();
        }
        return mvnHome;
    }

    private static String getNodeDefinedMavenHome(AbstractBuild<?, ?> build) {
        Node currentNode = build.getBuiltOn();
        DescribableList<NodeProperty<?>, NodePropertyDescriptor> properties = currentNode.getNodeProperties();
        ToolLocationNodeProperty toolLocation = properties.get(ToolLocationNodeProperty.class);
        if (toolLocation != null) {

            List<ToolLocationNodeProperty.ToolLocation> locations = toolLocation.getLocations();
            if (locations != null) {
                for (ToolLocationNodeProperty.ToolLocation location : locations) {
                    if (location.getType().isSubTypeOf(Maven.MavenInstallation.class)) {
                        String installationHome = location.getHome();
                        if (StringUtils.isNotBlank(installationHome)) {
                            return installationHome;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static String getEnvDefinedMavenHome(Map<String, String> env) {
        String mavenHome = env.get("MAVEN_HOME");
        if (StringUtils.isNotBlank(mavenHome)) {
            return mavenHome;
        }

        return env.get("M2_HOME");
    }

    /**
     * Copies a classworlds file to a temporary location either on the local filesystem or on a slave depending on the
     * node type.
     *
     * @return The path of the classworlds.conf file
     */
    public static String copyClassWorldsFile(AbstractBuild<?, ?> build, URL resource, File classWorldsFile) {
        String classworldsConfPath;
        if (Computer.currentComputer() instanceof SlaveComputer) {
            try {
                FilePath remoteClassworlds =
                        build.getWorkspace().createTextTempFile("classworlds", "conf", "", false);
                remoteClassworlds.copyFrom(resource);
                classworldsConfPath = remoteClassworlds.getRemote();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            classworldsConfPath = classWorldsFile.getAbsolutePath();
            File classWorldsConf = new File(resource.getFile());
            try {
                FileUtils.copyFile(classWorldsConf, classWorldsFile);
            } catch (IOException e) {
                build.setResult(Result.FAILURE);
                throw new RuntimeException(
                        "Unable to copy classworlds file: " + classWorldsConf.getAbsolutePath() + " to: " +
                                classWorldsFile.getAbsolutePath(), e);
            }
        }
        return classworldsConfPath;
    }


    /**
     * Add a custom {@code classworlds.conf} file that will be read by the Maven build. Adds an environment variable
     * {@code classwordls.conf} with the location of the classworlds file for Maven.
     *
     * @return The path of the classworlds.conf file
     */
    public static void addCustomClassworlds(Map<String, String> env, String classworldsConfPath) {
        env.put("classworlds.conf", classworldsConfPath);
    }

    /**
     * Add build info properties that will be read by an external extractor. All properties are then saved into a {@code
     * buildinfo.properties} into a temporary location. The location is then put into an environment variable {@link
     * BuildInfoConfigProperties#PROP_PROPS_FILE} for the extractor to read.
     *
     * @param env                       A map of the environment variables that are to be persisted into the
     *                                  buildinfo.properties file
     * @param build                     The build from which to get build/project related information from (e.g build
     *                                  name and build number).
     * @param selectedArtifactoryServer The Artifactory server that is to be used during the build for resolution/
     *                                  deployment
     * @param context                   A container object for build related data.
     */
    public static ArtifactoryClientConfiguration addBuilderInfoArguments(Map<String, String> env, AbstractBuild build,
            ArtifactoryServer selectedArtifactoryServer, BuildContext context)
            throws IOException, InterruptedException {
        ArtifactoryClientConfiguration configuration = new ArtifactoryClientConfiguration(new NullLog());
        configuration.setActivateRecorder(Boolean.TRUE);

        String buildName = build.getProject().getDisplayName();
        configuration.info.setBuildName(buildName);
        configuration.publisher.addMatrixParam("build.name", buildName);
        String buildNumber = build.getNumber() + "";
        configuration.info.setBuildNumber(buildNumber);
        configuration.publisher.addMatrixParam("build.number", buildNumber);

        Date buildStartDate = build.getTimestamp().getTime();
        configuration.info.setBuildStarted(String.valueOf(buildStartDate.getTime()));
        configuration.info.setBuildTimestamp(String.valueOf(buildStartDate.getTime()));
        configuration.publisher.addMatrixParam("build.timestamp", String.valueOf(buildStartDate.getTime()));

        String vcsRevision = env.get("SVN_REVISION");
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
        configuration.info.setAgentName("Hudson");
        configuration.info.setAgentVersion(build.getHudsonVersion());
        configuration.setContextUrl(selectedArtifactoryServer.getUrl());
        configuration.setTimeout(selectedArtifactoryServer.getTimeout());
        configuration.publisher.setRepoKey(context.getDetails().repositoryKey);
        if (StringUtils.isNotBlank(context.getDetails().downloadRepositoryKey)) {
            configuration.resolver.setRepoKey(context.getDetails().downloadRepositoryKey);
        }
        configuration.publisher.setSnapshotRepoKey(context.getDetails().snapshotsRepositoryKey);
        Credentials preferredDeployer =
                CredentialResolver.getPreferredDeployer(context.getDeployerOverrider(), selectedArtifactoryServer);
        if (StringUtils.isNotBlank(preferredDeployer.getUsername())) {
            configuration.publisher.setUserName(preferredDeployer.getUsername());
            configuration.publisher.setPassword(preferredDeployer.getPassword());
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
            }
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
        addBuildRootIfNeeded(build, configuration);
        configuration.publisher.setPublishBuildInfo(!context.isSkipBuildInfoDeploy());
        configuration.setIncludeEnvVars(context.isIncludeEnvVars());
        addEnvVars(env, build, configuration);
        persistConfiguration(build, configuration, env);
        return configuration;
    }

    public static void addBuildRootIfNeeded(AbstractBuild build, ArtifactoryClientConfiguration configuration) {
        AbstractBuild<?, ?> rootBuild = BuildUniqueIdentifierHelper.getRootBuild(build);
        if (BuildUniqueIdentifierHelper.isPassIdentifiedDownstream(rootBuild)) {
            String identifier = BuildUniqueIdentifierHelper.getUpstreamIdentifier(rootBuild);
            configuration.info.setBuildRoot(identifier);
        }
    }

    public static void persistConfiguration(AbstractBuild build, ArtifactoryClientConfiguration configuration,
            Map<String, String> env) throws IOException, InterruptedException {
        FilePath tempFile = build.getWorkspace().createTextTempFile("buildInfo", "properties", "", false);
        configuration.setPropertiesFile(tempFile.getRemote());
        env.putAll(configuration.getAllRootConfig());
        configuration.persistToPropertiesFile();
    }

    private static void addEnvVars(Map<String, String> env, AbstractBuild<?, ?> build,
            ArtifactoryClientConfiguration configuration) {
        // Write all the deploy (matrix params) properties.
        configuration.fillFromProperties(env);
        //Add only the hudson specific environment variables
        MapDifference<String, String> envDifference = Maps.difference(env, System.getenv());
        Map<String, String> filteredEnvDifference = envDifference.entriesOnlyOnLeft();
        configuration.info.addBuildVariables(filteredEnvDifference);

        // add build variables
        Map<String, String> buildVariables = build.getBuildVariables();
        configuration.fillFromProperties(buildVariables);
        Map<String, String> filteredBuildVars = Maps.newHashMap();

        MapDifference<String, String> buildVarDifference = Maps.difference(buildVariables, filteredBuildVars);
        Map<String, String> filteredBuildVarDifferences = buildVarDifference.entriesOnlyOnLeft();

        configuration.info.addBuildVariables(filteredBuildVarDifferences);
    }
}
