package org.jfrog.hudson.pipeline.docker;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Sets;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpResponse;
import org.jfrog.build.api.Artifact;
import org.jfrog.build.api.Dependency;
import org.jfrog.build.api.Module;
import org.jfrog.build.api.builder.ArtifactBuilder;
import org.jfrog.build.api.builder.DependencyBuilder;
import org.jfrog.build.api.search.AqlSearchResult;
import org.jfrog.build.client.ArtifactoryVersion;
import org.jfrog.build.extractor.clientConfiguration.client.ArtifactoryBuildInfoClient;
import org.jfrog.build.extractor.clientConfiguration.client.ArtifactoryDependenciesClient;
import org.jfrog.hudson.ArtifactoryServer;
import org.jfrog.hudson.CredentialsConfig;
import org.jfrog.hudson.pipeline.ArtifactoryConfigurator;
import org.jfrog.hudson.pipeline.docker.utils.DockerUtils;
import org.jfrog.hudson.util.CredentialManager;
import org.jfrog.hudson.util.ExtractorUtils;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;

/**
 * Created by romang on 8/9/16.
 */
public class DockerImage implements Serializable {
    private final String imageId;
    private final String imageTag;
    private final String targetRepo;
    // List of build-info IDs. These IDs link this docker image to the specific corresponding
    // build-info instances.
    private final HashSet<Integer> buildInfoIds = new HashSet<Integer>();
    private String manifest;
    private String agentName = "";
    // List of properties added to the build-info generated for this docker image.
    private Properties buildInfoNoduleProps = new Properties();
    // Properties to be attached to the docker layers deployed to Artifactory.
    private ArrayListMultimap<String, String> artifactsProps;
    private final ArtifactoryVersion VIRTUAL_REPOS_SUPPORTED_VERSION = new ArtifactoryVersion("4.8.1");

    public DockerImage(String imageId, String imageTag, String targetRepo) {
        this.imageId = imageId;
        this.imageTag = imageTag;
        this.targetRepo = targetRepo;
    }

    public void addBuildInfoId(int id) {
        buildInfoIds.add(id);
    }

    public boolean hasBuild(int buildInfoId) {
        return buildInfoIds.contains(buildInfoId);
    }

    public void removeBuild(int buildInfoId) {
        buildInfoIds.remove(buildInfoId);
    }

    public boolean hasBuilds() {
        return buildInfoIds.size() > 0;
    }

    public void setManifest(String manifest) {
        this.manifest = manifest;
    }

    public void setArtifactsProps(ArrayListMultimap<String, String> artifactsProps) {
        this.artifactsProps = artifactsProps;
    }

    /**
     * Indicates whether a manifest has been captured and attached for this image.
     * @return
     */
    public boolean hasManifest() {
        return StringUtils.isNotBlank(manifest);
    }

    public ArrayListMultimap<String, String> getArtifactsProps() {
        return artifactsProps;
    }

    public String getImageTag() {
        return imageTag;
    }

    public String getImageId() {
        return imageId;
    }

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    /**
     * Generates the build-info module for this docker image.
     * Additionally. this method tags the deployed docker layers with properties,
     * such as build.name, build.number and custom properties defined in the Jenkins build.
     * @param build
     * @param listener
     * @param config
     * @param buildName
     * @param buildNumber
     * @param timestamp
     * @return
     * @throws IOException
     */
    public Module generateBuildInfoModule(Run build, TaskListener listener, ArtifactoryConfigurator config, String buildName, String buildNumber, String timestamp) throws IOException {
        if (artifactsProps == null) {
            artifactsProps = ArrayListMultimap.create();
        }
        artifactsProps.put("build.name", buildName);
        artifactsProps.put("build.number", buildNumber);
        artifactsProps.put("build.timestamp", timestamp);
        String artifactsPropsStr = ExtractorUtils.buildPropertiesString(artifactsProps);

        Properties buildInfoItemsProps = new Properties();
        buildInfoItemsProps.setProperty("build.name", buildName);
        buildInfoItemsProps.setProperty("build.number", buildNumber);
        buildInfoItemsProps.setProperty("build.timestamp", timestamp);

        ArtifactoryServer server = config.getArtifactoryServer();
        CredentialsConfig preferredResolver = server.getDeployerCredentialsConfig();

        ArtifactoryDependenciesClient dependenciesClient = server.createArtifactoryDependenciesClient(
                preferredResolver.provideUsername(build.getParent()), preferredResolver.providePassword(build.getParent()),
                server.createProxyConfiguration(Jenkins.getInstance().proxy), listener);

        CredentialsConfig preferredDeployer = CredentialManager.getPreferredDeployer(config, server);
        ArtifactoryBuildInfoClient propertyChangeClient = server.createArtifactoryClient(
                preferredDeployer.provideUsername(build.getParent()), preferredDeployer.providePassword(build.getParent()),
                server.createProxyConfiguration(Jenkins.getInstance().proxy));

        Module buildInfoModule = new Module();
        buildInfoModule.setId(imageTag.substring(imageTag.indexOf("/") + 1));

        boolean includeVirtualReposSupported = propertyChangeClient.getArtifactoryVersion().isAtLeast(VIRTUAL_REPOS_SUPPORTED_VERSION);
        DockerLayers layers = createLayers(dependenciesClient, includeVirtualReposSupported);

        setDependenciesAndArtifacts(buildInfoModule, layers, artifactsPropsStr, buildInfoItemsProps,
                dependenciesClient, propertyChangeClient, server);
        setBuildInfoModuleProps(buildInfoModule);
        return buildInfoModule;
    }

    private void setBuildInfoModuleProps(Module buildInfoModule) {
        buildInfoNoduleProps.setProperty("docker.image.id", DockerUtils.getShaValue(imageId));
        buildInfoNoduleProps.setProperty("docker.captured.image", imageTag);
        buildInfoModule.setProperties(buildInfoNoduleProps);
    }

    public void addBuildInfoModuleProps(Properties props) {
        buildInfoNoduleProps.putAll(props);
    }

    private DockerLayers createLayers(ArtifactoryDependenciesClient dependenciesClient, boolean includeVirtualReposSupported) throws IOException {
        String queryStr = getAqlQuery(includeVirtualReposSupported);
        AqlSearchResult result = dependenciesClient.searchArtifactsByAql(queryStr);
        String imagePath = DockerUtils.getImagePath(imageTag);

        DockerLayers layers = new DockerLayers();
        for (AqlSearchResult.SearchEntry entry : result.getResults()) {
            if (!StringUtils.equals(entry.getPath(), imagePath)) {
                continue;
            }

            Set<String> virtual_repos = Sets.newHashSet(entry.getVirtualRepos());
            if (!(StringUtils.equals(entry.getRepo(), targetRepo) || virtual_repos.contains(targetRepo))) {
                continue;
            }

            DockerLayer layer = new DockerLayer(entry);
            layers.addLayer(layer);
        }
        return layers;
    }

    private void setDependenciesAndArtifacts(Module buildInfoModule, DockerLayers layers, String artifactsProps, Properties buildInfoItemsProps, ArtifactoryDependenciesClient dependenciesClient, ArtifactoryBuildInfoClient propertyChangeClient, ArtifactoryServer server) throws IOException {
        DockerLayer historyLayer = layers.getByDigest(imageId);
        if (historyLayer == null) {
            return;
        }
        HttpResponse res = dependenciesClient.downloadArtifact(server.getUrl() + "/" + historyLayer.getFullPath());
        int dependencyLayerNum = DockerUtils.getNumberOfDependentLayers(IOUtils.toString(res.getEntity().getContent()));

        List<Dependency> dependencies = new ArrayList<Dependency>();
        List<Artifact> artifacts = new ArrayList<Artifact>();
        Iterator<String> it = DockerUtils.getLayersDigests(manifest).iterator();
        for (int i = 0; i < dependencyLayerNum; i++) {
            String digest = it.next();
            DockerLayer layer = layers.getByDigest(digest);
            propertyChangeClient.executeUpdateFileProperty(layer.getFullPath(), artifactsProps);
            Dependency dependency = new DependencyBuilder().id(layer.getFileName()).sha1(layer.getSha1()).properties(buildInfoItemsProps).build();
            dependencies.add(dependency);

            Artifact artifact = new ArtifactBuilder(layer.getFileName()).sha1(layer.getSha1()).properties(buildInfoItemsProps).build();
            artifacts.add(artifact);
        }
        buildInfoModule.setDependencies(dependencies);

        while (it.hasNext()) {
            String digest = it.next();
            DockerLayer layer = layers.getByDigest(digest);
            if (layer == null) {
                continue;
            }
            propertyChangeClient.executeUpdateFileProperty(layer.getFullPath(), artifactsProps);
            Artifact artifact = new ArtifactBuilder(layer.getFileName()).sha1(layer.getSha1()).properties(buildInfoItemsProps).build();
            artifacts.add(artifact);
        }
        buildInfoModule.setArtifacts(artifacts);
    }

    /**
     * Prepare AQL query to get all the manifest layers from Artifactory.
     * Needed for build-info sha1/md5 checksum for each artifact and dependency.
     *
     * @return
     * @throws IOException
     */
    private String getAqlQuery(boolean includeVirtualRepos) throws IOException {
        List<String> layersDigest = DockerUtils.getLayersDigests(manifest);

        StringBuilder aqlRequestForDockerSha = new StringBuilder("items.find({\"$or\":[ ");
        List<String> layersQuery = new ArrayList<String>();
        for (String digest : layersDigest) {
            String shaVersion = DockerUtils.getShaVersion(digest);
            String shaValue = DockerUtils.getShaValue(digest);

            String singleFileQuery = String.format("{\"name\": \"%s\"}", DockerUtils.digestToFileName(digest));

            if (StringUtils.equalsIgnoreCase(shaVersion, "sha1")) {
                singleFileQuery = String.format("{\"actual_sha1\": \"%s\"}", shaValue);
            }
            layersQuery.add(singleFileQuery);
        }

        aqlRequestForDockerSha.append(StringUtils.join(layersQuery, ","));
        if (includeVirtualRepos) {
            aqlRequestForDockerSha.append("]}).include(\"name\",\"repo\",\"path\",\"actual_sha1\",\"virtual_repos\")");
        } else {
            aqlRequestForDockerSha.append("]}).include(\"name\",\"repo\",\"path\",\"actual_sha1\")");
        }
        return aqlRequestForDockerSha.toString();
    }
}
