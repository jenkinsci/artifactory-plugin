package org.jfrog.hudson.generic;

import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import hudson.FilePath;
import hudson.ProxyConfiguration;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Hudson;
import hudson.remoting.VirtualChannel;
import org.apache.commons.lang.StringUtils;
import org.jfrog.build.api.Dependency;
import org.jfrog.build.api.builder.DependencyBuilder;
import org.jfrog.build.api.dependency.BuildPatternArtifacts;
import org.jfrog.build.api.dependency.BuildPatternArtifactsRequest;
import org.jfrog.build.api.dependency.DownloadableArtifact;
import org.jfrog.build.api.dependency.PatternArtifact;
import org.jfrog.build.api.dependency.PatternResultFileSet;
import org.jfrog.build.api.dependency.UserBuildDependency;
import org.jfrog.build.client.ArtifactoryDependenciesClient;
import org.jfrog.build.util.BuildDependenciesHelper;
import org.jfrog.build.util.DependenciesHelper;
import org.jfrog.build.util.PublishedItemsHelper;
import org.jfrog.hudson.ArtifactoryServer;
import org.jfrog.hudson.util.Credentials;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves artifacts from Artifactory (published dependencies and build dependencies)
 * This class is used only in free style generic configurator.
 *
 * @author Shay Yaakov
 */
public class GenericArtifactsResolver {
    private final AbstractBuild build;
    private final ArtifactoryGenericConfigurator configurator;
    private final BuildListener listener;
    private final ArtifactoryDependenciesClient client;
    private String resolvePattern;

    public GenericArtifactsResolver(AbstractBuild build, ArtifactoryGenericConfigurator configurator,
            BuildListener listener, ArtifactoryDependenciesClient client) {
        this.build = build;
        this.configurator = configurator;
        this.listener = listener;
        this.client = client;
        resolvePattern = configurator.getResolvePattern();
    }

    public List<Dependency> retrievePublishedDependencies() throws IOException, InterruptedException {
        Multimap<String, String> patternPairs = PublishedItemsHelper.getPublishedItemsPatternPairs(resolvePattern);
        //since now patterns can also contain buildDependencies we need to filter those out
        /*patternPairs = Maps.filterKeys(patternPairs, new Predicate<String>() {
            public boolean apply(String input) {
                return !input.contains("@");
            }
        });*/

        List<Dependency> dependencies = Collections.emptyList();
        if (patternPairs.isEmpty()) {
            return dependencies;
        }

        listener.getLogger().println(
                "Beginning to resolve Build Info published dependencies from " + configurator.getArtifactoryServer().getUrl());
        dependencies = downloadDependencies(patternPairs);
        listener.getLogger().println("Finished resolving Build Info published dependencies.");

        return dependencies;
    }

    private List<Dependency> downloadDependencies(Multimap<String, String> patternPairs)
            throws IOException, InterruptedException {
        Set<DownloadableArtifact> downloadableArtifacts = Sets.newHashSet();
        for (Map.Entry<String, String> patternPair : patternPairs.entries()) {
            if (!patternPair.getKey().contains("@")) {
                downloadableArtifacts.addAll(handleDependencyPatternPair(patternPair));
            }
        }

        FilePath workspace = build.getWorkspace();
        Credentials preferredDeployer;
        ArtifactoryServer server = configurator.getArtifactoryServer();
        if (configurator.isOverridingDefaultDeployer()) {
            preferredDeployer = configurator.getOverridingDeployerCredentials();
        } else {
            preferredDeployer = server.getResolvingCredentials();
        }
        return workspace.act(new BuildDependenciesCallable(listener, Hudson.getInstance().proxy, preferredDeployer,
                server, true, downloadableArtifacts));
    }

    private Set<DownloadableArtifact> handleDependencyPatternPair(Map.Entry<String, String> patternPair)
            throws IOException {

        Set<DownloadableArtifact> downloadableArtifacts = Sets.newHashSet();
        String sourcePattern = patternPair.getKey();
        String pattern = DependenciesHelper.extractPatternFromSource(sourcePattern);
        String matrixParams = DependenciesHelper.extractMatrixParamsFromSource(sourcePattern);

        listener.getLogger().println("Resolving published dependencies with pattern " + sourcePattern);

        PatternResultFileSet fileSet = client.searchArtifactsByPattern(pattern);
        Set<String> filesToDownload = fileSet.getFiles();

        listener.getLogger().println("Found " + filesToDownload.size() + " dependencies.");

        for (String fileToDownload : filesToDownload) {
            downloadableArtifacts.add(new DownloadableArtifact(fileSet.getRepoUri(), patternPair.getValue(),
                    fileToDownload, matrixParams));
        }

        return downloadableArtifacts;
    }

    public List<UserBuildDependency> retrieveBuildDependencies() throws IOException, InterruptedException {
        List<UserBuildDependency> buildDependencies = BuildDependenciesHelper.getBuildDependencies(resolvePattern);

        /**
         * Don't run if dependencies mapping came out to be empty.
         */
        if (buildDependencies.isEmpty()) {
            return buildDependencies;
        }

        listener.getLogger().println("Beginning to resolve Build Info build dependencies from " +
                configurator.getArtifactoryServer().getUrl());

        List<BuildPatternArtifactsRequest> artifactsRequests = BuildDependenciesHelper.toArtifactsRequests(
                buildDependencies);
        List<BuildPatternArtifacts> artifacts = client.retrievePatternArtifacts(artifactsRequests);
        BuildDependenciesHelper.applyBuildArtifacts(buildDependencies, artifacts);

        downloadBuildDependencies(buildDependencies);
        listener.getLogger().println("Finished resolving Build Info build dependencies.");

        return buildDependencies;
    }

    private void downloadBuildDependencies(List<UserBuildDependency> userBuildDependencies)
            throws IOException, InterruptedException {
        Set<DownloadableArtifact> downloadableArtifacts = Sets.newHashSet();
        for (UserBuildDependency dependencyUser : userBuildDependencies) {

            final String message = String.format("Dependency on build [%s], number [%s]",
                    dependencyUser.getBuildName(), dependencyUser.getBuildNumberRequest());
            /**
             * dependency.getBuildNumberResponse() is null for unresolved dependencies (wrong build name or build number).
             */
            if (dependencyUser.getBuildNumberResponse() == null) {
                listener.getLogger().println(
                        message + " - no results found, check correctness of dependency build name and build number.");
            } else {

                for (UserBuildDependency.Pattern pattern : dependencyUser.getPatterns()) {

                    List<PatternArtifact> artifacts = pattern.getPatternResult().getPatternArtifacts();

                    listener.getLogger().println(message +
                            String.format(", pattern [%s] - [%s] result%s found.",
                                    pattern.getArtifactoryPattern(), artifacts.size(),
                                    (artifacts.size() == 1 ? "" : "s")));

                    for (PatternArtifact artifact : artifacts) {

                        final String uri = artifact.getUri(); // "libs-release-local/com/goldin/plugins/gradle/0.1.1/gradle-0.1.1.jar"
                        final int j = uri.indexOf('/');

                        assert (j > 0) : String.format("Filed to locate '/' in [%s]", uri);

                        final String repoUrl = artifact.getArtifactoryUrl() + '/' + uri.substring(0, j);
                        final String filePath = uri.substring(j + 1);
                        final String matrixParameters = pattern.getMatrixParameters();
                        downloadableArtifacts.add(new DownloadableArtifact(repoUrl, pattern.getTargetDirectory(),
                                filePath, matrixParameters));
                    }
                }
            }
        }

        FilePath workspace = build.getWorkspace();
        Credentials preferredDeployer;
        ArtifactoryServer server = configurator.getArtifactoryServer();
        if (configurator.isOverridingDefaultDeployer()) {
            preferredDeployer = configurator.getOverridingDeployerCredentials();
        } else {
            preferredDeployer = server.getResolvingCredentials();
        }
        workspace.act(new BuildDependenciesCallable(listener, Hudson.getInstance().proxy, preferredDeployer, server,
                false, downloadableArtifacts));
    }

    private static class BuildDependenciesCallable implements FilePath.FileCallable<List<Dependency>> {
        private final BuildListener listener;
        private final ProxyConfiguration proxy;
        private final Credentials credentials;
        private final ArtifactoryServer server;
        private final boolean includeDependencies;
        private Set<DownloadableArtifact> downloadableArtifacts;

        public BuildDependenciesCallable(final BuildListener listener, ProxyConfiguration proxy,
                Credentials credentials, ArtifactoryServer server, boolean includeDependencies,
                Set<DownloadableArtifact> downloadableArtifacts) {
            this.listener = listener;
            this.proxy = proxy;
            this.credentials = credentials;
            this.server = server;
            this.includeDependencies = includeDependencies;
            this.downloadableArtifacts = downloadableArtifacts;
        }

        public List<Dependency> invoke(File dest, VirtualChannel channel) throws IOException, InterruptedException {
            ArtifactoryDependenciesClient client = server.createArtifactoryDependenciesClient(
                    credentials.getUsername(), credentials.getPassword(), proxy, listener);
            List<Dependency> dependencies = Lists.newArrayList();
            try {
                for (DownloadableArtifact downloadableArtifact : downloadableArtifacts) {
                    Dependency dependency = downloadArtifact(client,
                            targetDir(downloadableArtifact.getRelativeDirPath(), dest),
                            downloadableArtifact.getRepoUrl(),
                            // "http://10.0.0.1:8080/artifactory/libs-release-local"
                            downloadableArtifact.getFilePath(),
                            // "com/goldin/plugins/gradle/0.1.1/gradle-0.1.1.jar"
                            downloadableArtifact.getMatrixParameters(), includeDependencies);
                    if (dependency != null) {
                        dependencies.add(dependency);
                    }

                }
            } finally {
                client.shutdown();
            }

            return dependencies;
        }

        private File targetDir(String targetDir, File workingDir) {
            final File targetDirFile = new File(targetDir);
            final File finalDir = targetDirFile.isAbsolute() ? targetDirFile : new File(workingDir, targetDir);
            return finalDir;
        }

        private Dependency downloadArtifact(ArtifactoryDependenciesClient client, File workingDir, String repoUri,
                String filePath, String matrixParams, boolean includeDependencies)
                throws IOException, InterruptedException {

            Dependency dependencyResult = null;
            final String uri = repoUri + '/' + filePath;
            final String uriWithParams = (StringUtils.isBlank(matrixParams) ? uri : uri + ';' + matrixParams);
            final File dest = new File(workingDir, filePath);

            listener.getLogger().println("Downloading '" + uriWithParams + "' ...");
            try {
                client.downloadArtifact(uriWithParams, dest);
                if (!dest.isFile()) {
                    throw new IOException(String.format("[%s] is not found!", dest.getCanonicalPath()));
                }

                listener.getLogger().println(
                        "Successfully downloaded '" + uriWithParams + "' into '" + dest.getCanonicalPath() + "'");

                if (includeDependencies) {

                    listener.getLogger().println("Retrieving checksums...");

                    String md5 = client.downloadChecksum(uri, "md5");
                    String sha1 = client.downloadChecksum(uri, "sha1");

                    DependencyBuilder builder = new DependencyBuilder().id(filePath).md5(md5).sha1(sha1);
                    dependencyResult = builder.build();
                }
            } catch (FileNotFoundException e) {
                dest.delete();
                String warningMessage = "Error occurred while resolving published dependency: " + e.getMessage();
                listener.getLogger().println(warningMessage);
            } catch (IOException e) {
                dest.delete();
                throw e;
            }

            return dependencyResult;
        }
    }
}
