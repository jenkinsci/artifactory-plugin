package org.jfrog.hudson.generic;

import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.jfrog.build.api.Dependency;
import org.jfrog.build.api.dependency.DownloadableArtifact;
import org.jfrog.build.api.util.FileChecksumCalculator;
import org.jfrog.build.api.util.Log;
import org.jfrog.build.client.ArtifactoryDependenciesClient;
import org.jfrog.build.util.DependenciesDownloader;
import org.jfrog.build.util.DependenciesDownloaderHelper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Primary implementation of dependencies downloader,
 * handles Jenkins slaves and re-use a client for HTTP communication.
 *
 * @author Shay Yaakov
 */
public class DependenciesDownloaderImpl implements DependenciesDownloader {

    private ArtifactoryDependenciesClient client;
    private FilePath workspace;
    private Log log;

    public DependenciesDownloaderImpl(ArtifactoryDependenciesClient client, FilePath workspace, Log log) {
        this.client = client;
        this.workspace = workspace;
        this.log = log;
    }

    public ArtifactoryDependenciesClient getClient() {
        return client;
    }

    public List<Dependency> download(Set<DownloadableArtifact> downloadableArtifacts) throws IOException {
        DependenciesDownloaderHelper helper = new DependenciesDownloaderHelper(this, log);
        return helper.downloadDependencies(downloadableArtifacts);
    }

    public String getTargetDir(String targetDir, String relativeDir) {
        FilePath targetDirFile = new FilePath(workspace, targetDir).child(relativeDir);
        return targetDirFile.getRemote();
    }

    public Map<String, String> saveDownloadedFile(InputStream is, String filePath) throws IOException {
        try {
            FilePath child = workspace.child(filePath);
            child.copyFrom(is);
            return child.act(new DownloadFileCallable(log));
        } catch (InterruptedException e) {
            log.warn("Caught interrupted exception: " + e.getLocalizedMessage());
        } finally {
            IOUtils.closeQuietly(is);
        }

        return null;
    }

    public boolean isFileExistsLocally(String filePath, String md5, String sha1) throws IOException {
        try {
            FilePath child = workspace.child(filePath);
            if (!child.exists()) {
                return false;
            }

            // If it's a folder return true since we don't care about it, not going to download a folder anyway
            if (child.isDirectory()) {
                return true;
            }

            Map<String, String> checksumsMap = child.act(new DownloadFileCallable(log));
            return checksumsMap != null &&
                    StringUtils.isNotBlank(md5) && StringUtils.equals(md5, checksumsMap.get("md5")) &&
                    StringUtils.isNotBlank(sha1) && StringUtils.equals(sha1, checksumsMap.get("sha1"));
        } catch (InterruptedException e) {
            log.warn("Caught interrupted exception: " + e.getLocalizedMessage());
        }

        return false;
    }

    private static class DownloadFileCallable implements FilePath.FileCallable<Map<String, String>> {
        private Log log;

        public DownloadFileCallable(Log log) {
            this.log = log;
        }

        public Map<String, String> invoke(File f, VirtualChannel channel) throws IOException {
            try {
                return FileChecksumCalculator.calculateChecksums(f, "md5", "sha1");
            } catch (NoSuchAlgorithmException e) {
                log.warn("Could not find checksum algorithm: " + e.getLocalizedMessage());
            }

            return null;
        }
    }
}
