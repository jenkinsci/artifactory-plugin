package org.jfrog.hudson.pipeline.integration;

import com.fasterxml.jackson.databind.JsonNode;
import hudson.FilePath;
import hudson.model.Slave;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import jenkins.model.Jenkins;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jfrog.artifactory.client.Artifactory;
import org.jfrog.artifactory.client.ArtifactoryRequest;
import org.jfrog.artifactory.client.RepositoryHandle;
import org.jfrog.artifactory.client.impl.ArtifactoryRequestImpl;
import org.jfrog.artifactory.client.model.LightweightRepository;
import org.jfrog.artifactory.client.model.RepoPath;
import org.jfrog.artifactory.client.model.RepositoryType;
import org.jfrog.build.extractor.ci.Artifact;
import org.jfrog.build.extractor.ci.BuildInfo;
import org.jfrog.build.extractor.ci.Dependency;
import org.jfrog.build.extractor.ci.Module;
import org.jfrog.build.api.util.Log;
import org.jfrog.build.extractor.clientConfiguration.client.artifactory.ArtifactoryManager;
import org.jfrog.build.extractor.clientConfiguration.client.response.GetAllBuildNumbersResponse;
import org.jfrog.build.extractor.docker.DockerJavaWrapper;
import org.jfrog.hudson.ArtifactoryServer;
import org.jfrog.hudson.JFrogPlatformInstance;
import org.jfrog.hudson.trigger.ArtifactoryTrigger;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.jfrog.artifactory.client.model.impl.RepositoryTypeImpl.LOCAL;
import static org.jfrog.artifactory.client.model.impl.RepositoryTypeImpl.REMOTE;
import static org.jfrog.artifactory.client.model.impl.RepositoryTypeImpl.VIRTUAL;
import static org.jfrog.hudson.TestUtils.getAndAssertChild;
import static org.jfrog.hudson.pipeline.integration.PipelineTestBase.artifactoryManager;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author yahavi
 */
class ITestUtils {

    private static final Pattern REPO_PATTERN = Pattern.compile("^jenkins-artifactory-tests(-\\w*)+-(\\d*)$");
    private static final Pattern BUILD_NUMBER_PATTERN = Pattern.compile("^/(\\d+)+-?(\\d*)$");
    private static final long currentTime = System.currentTimeMillis();

    /**
     * Get the integration tests dir.
     *
     * @return integration tests dir
     */
    static Path getIntegrationDir() {
        return Paths.get("src", "test", "resources", "integration");
    }

    /**
     * Escape backslashes in filesystem path.
     *
     * @param path - Filesystem path to fix
     * @return path compatible with Windows
     */
    static String fixWindowsPath(String path) {
        return StringUtils.replace(path, "\\", "\\\\");
    }

    /**
     * Clean up old test repositories.
     *
     * @param artifactoryClient - The Artifactory java client
     */
    static void cleanUpArtifactory(Artifactory artifactoryClient) {
        Arrays.asList(LOCAL, REMOTE, VIRTUAL).forEach(repoType -> cleanUpRepositoryType(artifactoryClient, repoType));
    }

    /**
     * Clean up old tests repositories with the specified type - Local, Remote or Virtual
     *
     * @param artifactoryClient - The Artifactory java client
     * @param repositoryType    - The repository type to delete
     */
    private static void cleanUpRepositoryType(Artifactory artifactoryClient, RepositoryType repositoryType) {
        artifactoryClient.repositories().list(repositoryType).stream()
                // Get repository key
                .map(LightweightRepository::getKey)

                // Match repository
                .map(REPO_PATTERN::matcher)
                .filter(Matcher::matches)

                // Filter repositories newer than 24 hours
                .filter(ITestUtils::isRepositoryOld)

                // Get repository key
                .map(Matcher::group)

                // Create repository handle
                .map(artifactoryClient::repository)

                // Delete repository
                .forEach(RepositoryHandle::delete);
    }

    /**
     * Clean up old build runs which have been created more than 24 hours ago.
     *
     * @param buildName - The build name to be cleaned.
     */
    public static void cleanOldBuilds(String buildName, String project) throws IOException {
        // Get build numbers for deletion
        String[] oldBuildNumbers = artifactoryManager.getAllBuildNumbers(buildName, project).buildsNumbers.stream()

                // Get build numbers.
                .map(GetAllBuildNumbersResponse.BuildsNumberDetails::getUri)

                //  Remove duplicates.
                .distinct()

                // Match build number pattern.
                .map(BUILD_NUMBER_PATTERN::matcher)
                .filter(Matcher::matches)

                // Filter build numbers newer than 24 hours.
                .filter(ITestUtils::isOldBuild)

                // Get build number.
                .map(matcher -> StringUtils.removeStart(matcher.group(), "/"))
                .toArray(String[]::new);

        if (oldBuildNumbers.length > 0) {
            artifactoryManager.deleteBuilds(buildName, project, true, oldBuildNumbers);
        }
    }

    /**
     * Return true if the repository was created more than 24 hours ago.
     *
     * @param repoMatcher - Repo regex matcher on REPO_PATTERN
     * @return true if the repository was created more than 24 hours ago
     */
    private static boolean isRepositoryOld(Matcher repoMatcher) {
        long repoTimestamp = Long.parseLong(repoMatcher.group(2));
        return TimeUnit.MILLISECONDS.toHours(currentTime - repoTimestamp) >= 24;
    }

    /**
     * Return true if the build was created more than 24 hours ago.
     *
     * @param buildMatcher - Build regex matcher on BUILD_NUMBER_PATTERN
     * @return true if the Build was created more than 24 hours ago
     */
    private static boolean isOldBuild(Matcher buildMatcher) {
        long repoTimestamp = Long.parseLong(buildMatcher.group(1));
        return TimeUnit.MILLISECONDS.toHours(currentTime - repoTimestamp) >= 0;
    }

    /**
     * Return true if the Artifact exists in the repository.
     *
     * @param artifactoryClient - Artifactory java client
     * @param repoKey           - Repository key
     * @param artifactName      - Artifact name
     * @return true if the artifact exists in the repository
     */
    static boolean isExistInArtifactory(Artifactory artifactoryClient, String repoKey, String artifactName) {
        RepositoryHandle repositoryHandle = artifactoryClient.repository(repoKey);
        try {
            repositoryHandle.file(artifactName).info();
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    /**
     * Return true if the file exists in the workspace under the input directory.
     *
     * @param slave    - Jenkins slave
     * @param build    - Jenkins job
     * @param dir      - Directory under workspace
     * @param fileName - File name to search
     * @return true if the file exists in the workspace under the input directory
     */
    static boolean isExistInWorkspace(Slave slave, WorkflowRun build, String dir, String fileName) throws IOException, InterruptedException {
        FilePath ws = slave.getWorkspaceFor(build.getParent());
        if (ws == null) {
            throw new IOException("Workspace for " + build.getDisplayName() + " not found");
        }
        ws = ws.child(dir);
        if (!ws.exists()) {
            throw new IOException("Directory " + ws.getRemote() + " doesn't exist");
        }
        return ws.child(fileName).exists();
    }

    /**
     * Use Artifactory java client to upload a file to Artifactory.
     *
     * @param artifactoryClient - Artifactory java client
     * @param source            - Source file to upload
     * @param repoKey           - Repository key
     */
    static void uploadFile(Artifactory artifactoryClient, Path source, String repoKey) {
        artifactoryClient.repository(repoKey).upload(source.getFileName().toString(), source.toFile()).doUpload();
    }

    /**
     * Get build info from Artifactory.
     *
     * @param artifactoryManager - ArtifactoryManager
     * @param buildName          - Build name
     * @param buildNumber        - Build number
     * @return build info for the specified build name and number
     */
    static BuildInfo getBuildInfo(ArtifactoryManager artifactoryManager, String buildName, String buildNumber, String project) throws IOException {
        return artifactoryManager.getBuildInfo(buildName, buildNumber, project);
    }

    /**
     * Assert that secret environment variables haven't been published.
     *
     * @param buildInfo - Build-info object
     */
    static void assertFilteredProperties(BuildInfo buildInfo) {
        Properties properties = buildInfo.getProperties();
        assertNotNull(properties);
        String[] unfiltered = properties.keySet().stream()
                .map(Object::toString)
                .map(String::toLowerCase)
                .filter(key -> StringUtils.containsAny(key, "password", "psw", "secret", "key", "token", "DONT_COLLECT"))
                .toArray(String[]::new);
        assertTrue("The following environment variables should have been filtered: " + Arrays.toString(unfiltered), ArrayUtils.isEmpty(unfiltered));
        assertTrue(properties.containsKey("buildInfo.env.COLLECT"));
    }

    /**
     * Assert that the module dependencies and the expected dependencies are equal.
     *
     * @param module               - Module to check
     * @param expectedDependencies - Expected dependencies
     */
    static void assertModuleDependencies(Module module, Set<String> expectedDependencies) {
        Set<String> actualDependencies = module.getDependencies().stream().map(Dependency::getId).collect(Collectors.toSet());
        assertEquals(expectedDependencies, actualDependencies);
    }

    /**
     * Assert that the module artifacts and the expected artifacts are equal.
     *
     * @param module            - Module to check
     * @param expectedArtifacts - Expected artifacts
     */
    static void assertModuleArtifacts(Module module, Set<String> expectedArtifacts) {
        Set<String> actualArtifacts = module.getArtifacts().stream().map(Artifact::getName).collect(Collectors.toSet());
        assertEquals(expectedArtifacts, actualArtifacts);
    }

    /**
     * Assert no artifacts in repository.
     *
     * @param artifactoryClient - Artifactory java client
     * @param repoKey           - Repository key
     */
    static void assertNoArtifactsInRepo(Artifactory artifactoryClient, String repoKey) {
        List<RepoPath> repoPaths = artifactoryClient.searches().repositories(repoKey).artifactsByName("*in").doSearch();
        assertTrue(repoPaths.isEmpty());
    }

    /**
     * Assert artifacts exist in repository.
     *
     * @param artifactoryClient - Artifactory java client
     * @param repoKey           - Repository key
     * @param expectedArtifacts - Expected artifacts
     */
    static void assertArtifactsInRepo(Artifactory artifactoryClient, String repoKey, Set<String> expectedArtifacts) {
        List<RepoPath> repoPaths = artifactoryClient.searches().repositories(repoKey).artifactsByName("*in").doSearch();
        Set<String> actualArtifacts = repoPaths.stream().map(RepoPath::getItemPath).collect(Collectors.toSet());
        assertEquals(expectedArtifacts, actualArtifacts);
    }

    /**
     * Get module from the build-info object and assert its existence.
     *
     * @param buildInfo  - Build-info object
     * @param moduleName - Module name
     * @return module from the build-info
     */
    static Module getAndAssertModule(BuildInfo buildInfo, String moduleName) {
        assertNotNull(buildInfo);
        assertNotNull(buildInfo.getModules());
        Module module = buildInfo.getModule(moduleName);
        assertNotNull(module);
        return module;
    }

    /**
     * Assert that the artifacts and dependencies lists are not empty in the input module.
     *
     * @param buildInfo  - Build info object
     * @param moduleName - Module name
     */
    static void assertModuleContainsArtifactsAndDependencies(BuildInfo buildInfo, String moduleName) {
        Module module = getAndAssertModule(buildInfo, moduleName);
        assertTrue(CollectionUtils.isNotEmpty(module.getArtifacts()));
        assertTrue(CollectionUtils.isNotEmpty(module.getDependencies()));
    }

    /**
     * Assert that the artifacts list is not empty in the input module.
     *
     * @param buildInfo  - Build info object
     * @param moduleName - Module name
     */
    static void assertModuleContainsArtifacts(BuildInfo buildInfo, String moduleName) {
        Module module = getAndAssertModule(buildInfo, moduleName);
        assertTrue(CollectionUtils.isNotEmpty(module.getArtifacts()));
    }

    /**
     * Assert Docker module contains "docker.image.id" and "docker.captured.image".
     *
     * @param module - Docker module
     */
    static void assertDockerModuleProperties(Module module) {
        Properties moduleProps = module.getProperties();
        assertTrue("Module " + module.getId() + " expected to contain 'docker.image.id' property.", moduleProps.containsKey("docker.image.id"));
        assertTrue("Module " + module.getId() + " expected to contain 'docker.captured.image' property.", moduleProps.containsKey("docker.captured.image"));
    }

    /**
     * Delete build in Artifactory.
     *
     * @param artifactoryClient - Artifactory java client
     * @param buildName         - Build name to delete
     */
    static void deleteBuild(Artifactory artifactoryClient, String buildName) throws IOException {
        artifactoryClient.restCall(new ArtifactoryRequestImpl()
                .method(ArtifactoryRequest.Method.DELETE)
                .apiUrl("api/build/" + encodeBuildName(buildName))
                .addQueryParam("deleteAll", "1")
                .addQueryParam("artifacts", "1"));
    }

    /**
     * Check Jenkins job info node for JFrog Pipelines tests.
     *
     * @param node - The node that container the Jenkins job info node.
     */
    static void checkJenkinsJobInfo(JsonNode node, boolean completed) {
        JsonNode jobInfo = getAndAssertChild(node, "jobInfo", null);
        JsonNode jobName = getAndAssertChild(jobInfo, "job-name", null);
        getAndAssertChild(jobInfo, "job-number", "1");
        if (completed) {
            JsonNode duration = getAndAssertChild(jobInfo, "duration", null);
            assertTrue(NumberUtils.isDigits(duration.asText()));
        }
        JsonNode startTime = getAndAssertChild(jobInfo, "start-time", null);
        assertTrue(NumberUtils.isDigits(startTime.asText()));
        JsonNode buildUrl = getAndAssertChild(jobInfo, "build-url", null);
        WorkflowJob job = (WorkflowJob) Jenkins.get().getItem(jobName.textValue());
        assertNotNull(job);
        WorkflowRun lastBuild = job.getLastBuild();
        assertEquals(lastBuild.getParent().getAbsoluteUrl() + lastBuild.getNumber(), buildUrl.asText());
    }

    /**
     * Check artifactory build trigger.
     *
     * @param run - The pipeline run object
     * @return the artifactory trigger.
     */
    static ArtifactoryTrigger checkArtifactoryTrigger(WorkflowRun run) {
        Map<TriggerDescriptor, Trigger<?>> triggers = run.getParent().getTriggers();
        assertNotNull(triggers);
        ArtifactoryTrigger artifactoryTrigger = (ArtifactoryTrigger) triggers.values().stream()
                .filter(trigger -> trigger instanceof ArtifactoryTrigger)
                .findAny().orElse(null);
        assertNotNull(artifactoryTrigger);
        ArtifactoryServer server = artifactoryTrigger.getArtifactoryServer();
        assertNotNull(server);
        List<JFrogPlatformInstance> jfrogInstances = artifactoryTrigger.getJfrogInstances();
        assertTrue(jfrogInstances.stream()
                .filter(s -> StringUtils.equals(s.getId(), server.getServerId()))
                .anyMatch(s -> StringUtils.equals(s.getArtifactoryUrl(), server.getArtifactoryUrl())));
        assertEquals("libs-release-local", artifactoryTrigger.getPaths());
        assertEquals("* * * * *", artifactoryTrigger.getSpec());
        return artifactoryTrigger;
    }

    private static String encodeBuildName(String buildName) throws UnsupportedEncodingException {
        return URLEncoder.encode(buildName, "UTF-8").replace("+", "%20");
    }

    public static String getImageId(String image, String host, Log logger) {
        String id = DockerJavaWrapper.InspectImage(image, host, Collections.emptyMap(), logger).getId();
        assertNotNull(id);
        return id.replace(":", "__");
    }

    public static void cleanupBuilds(WorkflowRun pipelineResults, String buildName, String project, String... buildNumbers) throws IOException {
        if (pipelineResults != null && Objects.requireNonNull(pipelineResults.getResult()).completeBuild) {
            artifactoryManager.deleteBuilds(buildName, project, true, buildNumbers);
        }
        cleanOldBuilds(buildName, project);
    }
}
