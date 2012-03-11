package org.jfrog.hudson.generic;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.remoting.VirtualChannel;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.jfrog.build.api.BuildInfoFields;
import org.jfrog.build.api.util.FileChecksumCalculator;
import org.jfrog.build.client.ArtifactoryBuildInfoClient;
import org.jfrog.build.client.DeployDetails;
import org.jfrog.build.util.PublishedItemsHelper;
import org.jfrog.hudson.ArtifactoryServer;
import org.jfrog.hudson.util.ExtractorUtils;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Set;

/**
 * Resolves and deploys artifacts to Artifactory. This class is used only in free style generic configurator.
 *
 * @author Shay Yaakov
 */
public class GenericArtifactsDeployer {
    private static final String SHA1 = "SHA1";
    private static final String MD5 = "MD5";

    private AbstractBuild build;
    private ArtifactoryGenericConfigurator configurator;
    private BuildListener listener;
    private ArtifactoryBuildInfoClient client;
    private EnvVars env;
    private Set<DeployDetails> artifactsToDeploy = Sets.newHashSet();

    public GenericArtifactsDeployer(AbstractBuild build, ArtifactoryGenericConfigurator configurator,
            BuildListener listener, ArtifactoryBuildInfoClient client)
            throws IOException, InterruptedException, NoSuchAlgorithmException {
        this.build = build;
        this.configurator = configurator;
        this.listener = listener;
        this.client = client;
        this.env = build.getEnvironment(listener);

        assembleArtifactsToDeploy();
    }

    public void deploy() throws IOException {
        ArtifactoryServer artifactoryServer = configurator.getArtifactoryServer();
        for (DeployDetails deployDetail : artifactsToDeploy) {
            StringBuilder deploymentPathBuilder = new StringBuilder(artifactoryServer.getUrl());
            deploymentPathBuilder.append("/").append(configurator.getRepositoryKey());
            if (!deployDetail.getArtifactPath().startsWith("/")) {
                deploymentPathBuilder.append("/");
            }
            deploymentPathBuilder.append(deployDetail.getArtifactPath());
            listener.getLogger().println("Deploying artifact: " + deploymentPathBuilder.toString());
            client.deployArtifact(deployDetail);
        }
    }

    public Set<DeployDetails> getDeployedArtifacts() {
        return artifactsToDeploy;
    }

    private void assembleArtifactsToDeploy()
            throws IOException, InterruptedException, NoSuchAlgorithmException {
        FilePath workspace = build.getWorkspace();
        Multimap<String, File> filesMap = buildTargetPathToFiles(workspace);
        for (Map.Entry<String, File> entry : filesMap.entries()) {
            artifactsToDeploy.addAll(buildDeployDetailsFromFileEntry(entry, workspace.getRemote()));
        }
    }

    private Set<DeployDetails> buildDeployDetailsFromFileEntry(Map.Entry<String, File> fileEntry, String rootDir)
            throws IOException, NoSuchAlgorithmException {
        Set<DeployDetails> result = Sets.newHashSet();
        String targetPath = fileEntry.getKey();
        File artifactFile = fileEntry.getValue();
        String relativePath = artifactFile.getAbsolutePath();
        if (StringUtils.startsWith(relativePath, rootDir)) {
            relativePath = StringUtils.removeStart(artifactFile.getAbsolutePath(), rootDir);
        } else {
            String parentDir = artifactFile.getParent();
            if (!StringUtils.isBlank(parentDir)) {
                relativePath = StringUtils.removeStart(artifactFile.getAbsolutePath(),
                        parentDir);
            }
        }
        relativePath = FilenameUtils.separatorsToUnix(relativePath);
        relativePath = StringUtils.removeStart(relativePath, "/");
        String path = PublishedItemsHelper.calculateTargetPath(relativePath, targetPath, artifactFile.getName());
        path = StringUtils.replace(path, "//", "/");

        // calculate the sha1 checksum that is not given by Jenkins and add it to the deploy artifactsToDeploy
        Map<String, String> checksums = FileChecksumCalculator.calculateChecksums(artifactFile, SHA1, MD5);
        DeployDetails.Builder builder = new DeployDetails.Builder()
                .file(artifactFile)
                .artifactPath(path)
                .targetRepository(configurator.getRepositoryKey())
                .md5(checksums.get(MD5)).sha1(checksums.get(SHA1))
                .addProperty("build.name", build.getParent().getDisplayName())
                .addProperty("build.number", build.getNumber() + "")
                .addProperty("build.timestamp", build.getTimestamp().getTime().getTime() + "");
        String revision = ExtractorUtils.getVcsRevision(env);
        if (StringUtils.isNotBlank(revision)) {
            builder.addProperty(BuildInfoFields.VCS_REVISION, revision);
        }
        addMatrixParams(builder);
        result.add(builder.build());

        return result;
    }

    private Multimap<String, File> buildTargetPathToFiles(FilePath workingDir)
            throws IOException, InterruptedException {
        final Multimap<String, File> result = HashMultimap.create();
        String deployPattern = configurator.getDeployPattern();
        deployPattern = StringUtils.replace(deployPattern, "\r\n", "\n");
        deployPattern = StringUtils.replace(deployPattern, ",", "\n");
        Map<String, String> pairs = PublishedItemsHelper.getPublishedItemsPatternPairs(deployPattern);
        if (pairs.isEmpty()) {
            return result;
        }

        for (final Map.Entry<String, String> entry : pairs.entrySet()) {
            workingDir.act(new FilePath.FileCallable<Object>() {
                public Object invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
                    Multimap<String, File> publishingData = PublishedItemsHelper.buildPublishingData(f, entry.getKey(),
                            entry.getValue());
                    if (publishingData != null) {
                        listener.getLogger().println(
                                "For pattern: " + entry.getKey() + " " + publishingData.size() + " artifacts were found");
                        result.putAll(publishingData);
                    } else {
                        listener.getLogger().println("For pattern: " + entry.getKey() + " no artifacts were found");
                    }
                    return null;
                }
            });
        }
        return result;
    }

    private void addMatrixParams(DeployDetails.Builder builder) {
        String[] matrixParams = StringUtils.split(configurator.getMatrixParams(), "; ");
        if (matrixParams == null) {
            return;
        }
        for (String matrixParam : matrixParams) {
            String[] split = StringUtils.split(matrixParam, '=');
            if (split.length == 2) {
                String value = Util.replaceMacro(split[1], env);
                builder.addProperty(split[0], value);
            }
        }
    }

}
