package org.jfrog.hudson;

import hudson.maven.MavenBuild;
import hudson.maven.MavenModule;
import hudson.maven.MavenModuleSetBuild;
import hudson.maven.reporters.MavenAbstractArtifactRecord;
import hudson.maven.reporters.MavenAggregatedArtifactRecord;
import hudson.maven.reporters.MavenArtifact;
import hudson.maven.reporters.MavenArtifactRecord;
import hudson.model.BuildListener;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.FileEntity;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;

/**
 * Deploys artifacts to Artifactory.
 *
 * @author Yossi Shaul
 */
public class ArtifactsDeployer {
    public static boolean debug = Boolean.getBoolean(ArtifactsDeployer.class.getName() + ".debug");

    private final ArtifactoryServer artifactoryServer;
    private final String targetRepository;
    private final ArtifactoryRedeployPublisher artifactoryPublisher;
    private final MavenModuleSetBuild mavenModuleSetBuild;
    private final MavenAbstractArtifactRecord mar;
    private final BuildListener listener;


    public ArtifactsDeployer(ArtifactoryRedeployPublisher artifactoryPublisher,
            MavenModuleSetBuild mavenModuleSetBuild, MavenAbstractArtifactRecord mar, BuildListener listener) {
        this.artifactoryPublisher = artifactoryPublisher;
        this.mavenModuleSetBuild = mavenModuleSetBuild;
        this.mar = mar;
        this.listener = listener;
        this.artifactoryServer = artifactoryPublisher.getArtifactoryServer();
        this.targetRepository = artifactoryPublisher.getRepositoryKey();
    }

    public void deploy() throws IOException {
        listener.getLogger().println("Deploying artifacts to " + artifactoryServer.getUrl());
        PreemptiveHttpClient client = artifactoryServer.createHttpClient(
                artifactoryPublisher.getUsername(), artifactoryPublisher.getPassword());
        MavenAggregatedArtifactRecord mar2 = (MavenAggregatedArtifactRecord) mar;
        MavenModuleSetBuild moduleSetBuild = mar2.getBuild();
        Map<MavenModule, MavenBuild> mavenBuildMap = moduleSetBuild.getModuleLastBuilds();
        for (Map.Entry<MavenModule, MavenBuild> mavenBuildEntry : mavenBuildMap.entrySet()) {
            MavenBuild mavenBuild = mavenBuildEntry.getValue();
            MavenArtifactRecord mar = mavenBuild.getAction(MavenArtifactRecord.class);
            listener.getLogger().println("Deploying artifacts of module: " + mavenBuildEntry.getKey().getName());
            MavenArtifact mavenArtifact = mar.mainArtifact;
            deployArtifact(client, mavenBuild, mavenArtifact);
            if (!mar.isPOM()) {
                deployArtifact(client, mavenBuild, mar.pomArtifact);
            }

            for (MavenArtifact attachedArtifact : mar.attachedArtifacts) {
                deployArtifact(client, mavenBuild, attachedArtifact);
            }
        }
        client.shutdown();
    }

    private void deployArtifact(PreemptiveHttpClient client, MavenBuild mavenBuild, MavenArtifact mavenArtifact)
            throws IOException {
        String artifactPath = buildArtifactPath(mavenArtifact);
        StringBuilder deploymentPath = new StringBuilder();
        deploymentPath.append(artifactoryServer.getUrl()).append("/").append(targetRepository)
                .append("/").append(artifactPath)
                .append(";build.name=").append(urlEncode(mavenModuleSetBuild.getParent().getDisplayName()))
                .append(";build.number=").append(urlEncode(mavenModuleSetBuild.getNumber() + ""));
        File mainArtifactFile = getArtifactFile(mavenBuild, mavenArtifact);
        listener.getLogger().println("Deploying artifact: " + deploymentPath);
        uploadFile(client, mainArtifactFile, deploymentPath.toString());
    }

    private String urlEncode(String value) throws UnsupportedEncodingException {
        return URLEncoder.encode(value, "UTF-8");
    }

    private String buildArtifactPath(MavenArtifact mavenArtifact) {
        String directoryPath =
                mavenArtifact.groupId.replace('.', '/') + "/" + mavenArtifact.artifactId + "/" + mavenArtifact.version;

        String fileExtension = mavenArtifact.canonicalName.substring(mavenArtifact.canonicalName.lastIndexOf('.') + 1);
        String deployableFileName =
                mavenArtifact.artifactId + "-" + mavenArtifact.version
                        + (mavenArtifact.classifier != null ? "-" + mavenArtifact.classifier : "")
                        + "." + fileExtension;

        return directoryPath + "/" + deployableFileName;
    }

    /**
     * Obtains the {@link java.io.File} representing the archived artifact.
     */
    private File getArtifactFile(MavenBuild build, MavenArtifact mavenArtifact) throws FileNotFoundException {
        File file = new File(new File(new File(new File(build.getArtifactsDir(), mavenArtifact.groupId),
                mavenArtifact.artifactId), mavenArtifact.version), mavenArtifact.fileName);
        if (!file.exists()) {
            throw new FileNotFoundException("Archived artifact is missing: " + file);
        }
        return file;
    }

    /**
     * Send the file to the server
     */
    void uploadFile(PreemptiveHttpClient client, File file, String uploadUrl) throws IOException {
        HttpPut httpPut = new HttpPut(uploadUrl);
        FileEntity fileEntity = new FileEntity(file, "binary/octet-stream");
        httpPut.setEntity(fileEntity);
        HttpResponse response = client.execute(httpPut);
        StatusLine statusLine = response.getStatusLine();
        if (response.getEntity() != null) {
            response.getEntity().consumeContent();
        }

        if (statusLine.getStatusCode() != HttpStatus.SC_OK) {
            throw new IOException("Failed to deploy file: " + statusLine.getReasonPhrase());
        }
    }
}
