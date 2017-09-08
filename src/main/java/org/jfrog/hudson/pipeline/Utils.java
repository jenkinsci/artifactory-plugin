package org.jfrog.hudson.pipeline;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.plugins.git.util.BuildData;
import hudson.remoting.Callable;
import hudson.remoting.Channel;
import hudson.remoting.LocalChannel;
import hudson.remoting.VirtualChannel;
import hudson.util.ArgumentListBuilder;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Ref;
import org.jfrog.build.api.Vcs;
import org.jfrog.build.extractor.clientConfiguration.IncludeExcludePatterns;
import org.jfrog.hudson.CredentialsConfig;
import org.jfrog.hudson.pipeline.docker.proxy.CertManager;
import org.jfrog.hudson.pipeline.types.ArtifactoryServer;
import org.jfrog.hudson.pipeline.types.DistributionConfig;
import org.jfrog.hudson.pipeline.types.PromotionConfig;
import org.jfrog.hudson.pipeline.types.buildInfo.BuildInfo;
import org.jfrog.hudson.util.IncludesExcludes;
import org.jfrog.hudson.util.RepositoriesUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.*;

/**
 * Created by romang on 4/24/16.
 */
public class Utils {

    public static final String BUILD_INFO_DELIMITER = ".";
    public static final String CONAN_USER_HOME = "CONAN_USER_HOME";
    private static final String UNIX_GLOB_CHARS = "*?[]";

    /**
     * Prepares Artifactory server either from serverID or from ArtifactoryServer.
     *
     * @param artifactoryServerID
     * @param pipelineServer
     * @return
     */
    public static org.jfrog.hudson.ArtifactoryServer prepareArtifactoryServer(String artifactoryServerID,
                                                                              ArtifactoryServer pipelineServer) {

        if (artifactoryServerID == null && pipelineServer == null) {
            return null;
        }
        if (artifactoryServerID != null && pipelineServer != null) {
            return null;
        }
        if (pipelineServer != null) {
            CredentialsConfig credentials = pipelineServer.createCredentialsConfig();

            return new org.jfrog.hudson.ArtifactoryServer(null, pipelineServer.getUrl(), credentials,
                    credentials, pipelineServer.getConnection().getTimeout(), pipelineServer.isBypassProxy(), pipelineServer.getConnection().getRetry());
        }
        org.jfrog.hudson.ArtifactoryServer server = RepositoriesUtils.getArtifactoryServer(artifactoryServerID, RepositoriesUtils.getArtifactoryServers());
        if (server == null) {
            return null;
        }
        return server;
    }

    public static ListBoxModel getServerListBox() {
        ListBoxModel r = new ListBoxModel();
        List<org.jfrog.hudson.ArtifactoryServer> servers = RepositoriesUtils.getArtifactoryServers();
        r.add("", "");
        for (org.jfrog.hudson.ArtifactoryServer server : servers) {
            r.add(server.getName() + Utils.BUILD_INFO_DELIMITER + server.getUrl(), server.getName());
        }
        return r;
    }

    public static BuildInfo prepareBuildinfo(Run build, BuildInfo buildinfo) {
        if (buildinfo == null) {
            return new BuildInfo(build);
        }
        return buildinfo;
    }

    public static ObjectMapper mapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    public static EnvVars extractBuildParameters(Run build, TaskListener listener) {
        EnvVars buildVariables = new EnvVars();
        try {
            ParametersAction parameters = build.getAction(ParametersAction.class);
            if (parameters != null) {
                for (ParameterValue p : parameters) {
                    if (p.isSensitive()) {
                        continue;
                    }
                    String v = p.createVariableResolver(null).resolve(p.getName());
                    if (v != null) {
                        buildVariables.put(p.getName(), v);
                    }
                }
            }
        } catch (Exception e) {
            listener.getLogger().println("Can't get build variables");
            return null;
        }

        return buildVariables;
    }

    public static List<Vcs> extractVcsBuildData(Run build) {
        List<Vcs> result = new ArrayList<Vcs>();
        List<BuildData> buildData = build.getActions(BuildData.class);
        if (buildData != null) {
            for (BuildData data : buildData) {
                String sha1 = data.getLastBuiltRevision().getSha1String();
                Iterator<String> iterator = data.getRemoteUrls().iterator();
                if (iterator.hasNext()) {
                    result.add(new Vcs(iterator.next(), sha1));
                }
            }
        }
        return result;
    }

    public static String extractVcsRevision(FilePath filePath) throws IOException, InterruptedException {
        if (filePath == null) {
            return "";
        }
        FilePath dotGitPath = new FilePath(filePath, ".git");
        if (dotGitPath.exists()) {
            return dotGitPath.act(new FilePath.FileCallable<String>() {
                public String invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
                    FileRepository repository = new FileRepository(f);
                    Ref head = repository.getRef("HEAD");
                    return head.getObjectId().getName();
                }
            });
        }
        return extractVcsRevision(filePath.getParent());
    }

    public static Node getNode(Launcher launcher) {
        Node node = null;
        Jenkins j = Jenkins.getInstance();
        for (Computer c : j.getComputers()) {
            if (c.getChannel() == launcher.getChannel()) {
                node = c.getNode();
                break;
            }
        }
        return node;
    }

    public static Computer getCurrentComputer(Launcher launcher) {
        Jenkins j = Jenkins.getInstance();
        for (Computer c : j.getComputers()) {
            if (c.getChannel() == launcher.getChannel()) {
                return c;
            }
        }
        return null;
    }

    public static IncludesExcludes getArtifactsIncludeExcludeForDeyployment(IncludeExcludePatterns patternFilter) {
        if (patternFilter == null) {
            return new IncludesExcludes("", "");
        }
        String[] excludePatterns = patternFilter.getExcludePatterns();
        String[] includePatterns = patternFilter.getIncludePatterns();
        StringBuilder include = new StringBuilder();
        StringBuilder exclude = new StringBuilder();
        for (int i = 0; i < includePatterns.length; i++) {
            if (i < includePatterns.length - 1 && include.length() > 0) {
                include.append(", ");
            }
            include.append(includePatterns[i]);
        }
        for (int i = 0; i < excludePatterns.length; i++) {
            if (i < excludePatterns.length - 1 && exclude.length() > 0) {
                exclude.append(", ");
            }
            exclude.append(excludePatterns[i]);
        }

        IncludesExcludes result = new IncludesExcludes(include.toString(), exclude.toString());
        return result;
    }

    public static org.jfrog.build.api.Build getGeneratedBuildInfo(Run build, TaskListener listener, Launcher launcher, String jsonBuildPath) {
        ObjectMapper mapper = new ObjectMapper();
        FilePath generatedBuildInfoFilePath = null;
        InputStream inputStream = null;
        try {
            StringWriter writer = new StringWriter();
            generatedBuildInfoFilePath = new FilePath(launcher.getChannel(), jsonBuildPath);
            inputStream = generatedBuildInfoFilePath.read();
            IOUtils.copy(inputStream, writer, "UTF-8");
            String buildInfoFileContent = writer.toString();
            if (StringUtils.isBlank(buildInfoFileContent)) {
                return new org.jfrog.build.api.Build();
            }
            return mapper.readValue(buildInfoFileContent, org.jfrog.build.api.Build.class);
        } catch (Exception e) {
            listener.error("Couldn't read generated build info at : " + jsonBuildPath);
            build.setResult(Result.FAILURE);
            throw new Run.RunnerAbortedException();
        } finally {
            if (inputStream != null) {
                IOUtils.closeQuietly(inputStream);
            }
            deleteFilePathQuietly(generatedBuildInfoFilePath);
        }
    }

    private static void deleteFilePathQuietly(FilePath filePath) {
        try {
            if (filePath != null && filePath.exists()) {
                filePath.delete();
            }
        } catch (Exception e) {
            // Ignore exceptions
        }
    }

    public static void copyCertsToAgent(Computer c) throws IOException, InterruptedException {
        if (!(c instanceof Jenkins.MasterComputer)) {

            String certPath = CertManager.DEFAULT_RELATIVE_CERT_PATH;
            FilePath remotePublicKey = new FilePath(c.getChannel(), c.getNode().getRootPath() + "/" + certPath);
            FilePath localPublicKey = new FilePath(Jenkins.getInstance().getRootPath(), certPath);
            localPublicKey.copyTo(remotePublicKey);

            String keyPath = CertManager.DEFAULT_RELATIVE_KEY_PATH;
            FilePath remotePrivateKey = new FilePath(c.getChannel(), c.getNode().getRootPath() + "/" + keyPath);
            FilePath localPrivateKey = new FilePath(Jenkins.getInstance().getRootPath(), keyPath);
            localPrivateKey.copyTo(remotePrivateKey);
        }
    }

    public static String createTempJsonFile(Launcher launcher, final String name) throws Exception {
        return launcher.getChannel().call(new Callable<String, Exception>() {
            public String call() throws IOException {
                File tempFile = File.createTempFile(name, ".json");
                tempFile.deleteOnExit();
                return tempFile.getAbsolutePath();
            }
        });
    }

    public static void exeConan(ArgumentListBuilder args, FilePath pwd, Launcher launcher, TaskListener listener, Run build, EnvVars env) {
        boolean failed;
        try {
            if (!pwd.exists()) {
                pwd.mkdirs();
            }
            if (launcher.isUnix()) {
                boolean hasMaskedArguments = args.hasMaskedArguments();
                StringBuilder sb = new StringBuilder();
                for (String arg : args.toList()) {
                    sb.append(escapeUnixArgument(arg)).append(" ");
                }
                args.clear();
                args.add("sh", "-c");
                if (hasMaskedArguments) {
                    args.addMasked(sb.toString());
                } else {
                    args.add(sb.toString());
                }
            } else {
                args = args.toWindowsCommand();
            }
            int exitValue = launcher.launch().cmds(args).envs(env).stdout(listener).pwd(pwd).join();
            failed = (exitValue != 0);
        } catch (Exception e) {
            listener.error("Couldn't execute the conan client executable. " + e.getMessage());
            build.setResult(Result.FAILURE);
            throw new Run.RunnerAbortedException();
        }
        if (failed) {
            build.setResult(Result.FAILURE);
            throw new Run.RunnerAbortedException();
        }
    }

    private static String escapeUnixArgument(String arg) {
        StringBuilder res = new StringBuilder();
        for (char c : arg.toCharArray()) {
            if (UNIX_GLOB_CHARS.indexOf(c) >= 0) {
                res.append("\\");
            }
            res.append(c);
        }
        return res.toString();
    }

    public static PromotionConfig createPromotionConfig(Map<String, Object> promotionParams, boolean isTargetRepositoryMandatory) {
        final String targetRepository = "targetRepo";
        List<String> mandatoryParams = new ArrayList<String>(Arrays.asList(ArtifactoryServer.BUILD_NAME, ArtifactoryServer.BUILD_NUMBER));
        List<String> allowedParams = Arrays.asList(ArtifactoryServer.BUILD_NAME, ArtifactoryServer.BUILD_NUMBER, targetRepository, "sourceRepo", "status", "comment", "includeDependencies", "copy", "failFast");

        if (isTargetRepositoryMandatory) {
            mandatoryParams.add(targetRepository);
        }
        if (!promotionParams.keySet().containsAll(mandatoryParams)) {
            throw new IllegalArgumentException(mandatoryParams.toString() + " are mandatory arguments");
        }
        if (!allowedParams.containsAll(promotionParams.keySet())) {
            throw new IllegalArgumentException("Only the following arguments are allowed: " + allowedParams.toString());
        }
        final ObjectMapper mapper = new ObjectMapper();
        PromotionConfig config = mapper.convertValue(promotionParams, PromotionConfig.class);

        return config;
    }

    public static org.jfrog.hudson.release.promotion.PromotionConfig convertPromotionConfig(PromotionConfig pipelinePromotionConfig) {
        org.jfrog.hudson.release.promotion.PromotionConfig promotionConfig = new org.jfrog.hudson.release.promotion.PromotionConfig();
        promotionConfig.setBuildName(pipelinePromotionConfig.getBuildName());
        promotionConfig.setBuildNumber(pipelinePromotionConfig.getBuildNumber());
        promotionConfig.setTargetRepo(pipelinePromotionConfig.getTargetRepo());
        promotionConfig.setSourceRepo(pipelinePromotionConfig.getSourceRepo());
        promotionConfig.setStatus(pipelinePromotionConfig.getStatus());
        promotionConfig.setComment(pipelinePromotionConfig.getComment());
        promotionConfig.setIncludeDependencies(pipelinePromotionConfig.isIncludeDependencies());
        promotionConfig.setCopy(pipelinePromotionConfig.isCopy());
        promotionConfig.setFailFast(pipelinePromotionConfig.isFailFast());
        return promotionConfig;
    }

    public static DistributionConfig createDistributionConfig(Map<String, Object> promotionParams) {
        List<String> mandatoryParams = new ArrayList<String>(Arrays.asList(ArtifactoryServer.BUILD_NAME, ArtifactoryServer.BUILD_NUMBER, "targetRepo"));
        List<String> allowedParams = Arrays.asList(ArtifactoryServer.BUILD_NAME, ArtifactoryServer.BUILD_NUMBER, "publish", "overrideExistingFiles", "gpgPassphrase", "async", "targetRepo", "sourceRepos", "dryRun");

        if (!promotionParams.keySet().containsAll(mandatoryParams)) {
            throw new IllegalArgumentException(mandatoryParams.toString() + " are mandatory arguments");
        }
        if (!allowedParams.containsAll(promotionParams.keySet())) {
            throw new IllegalArgumentException("Only the following arguments are allowed: " + allowedParams.toString());
        }
        final ObjectMapper mapper = new ObjectMapper();
        DistributionConfig config = mapper.convertValue(promotionParams, DistributionConfig.class);

        return config;
    }

    public static String getAgentName(FilePath ws) {
        if (ws.getChannel() != null) {
            return ws.getChannel() instanceof LocalChannel ? "Master" : ((Channel) ws.getChannel()).getName();
        }
        return "Unknown";
    }
}
