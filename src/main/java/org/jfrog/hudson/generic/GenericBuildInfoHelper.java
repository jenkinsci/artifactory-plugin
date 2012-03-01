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

package org.jfrog.hudson.generic;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.remoting.VirtualChannel;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.resources.FileResource;
import org.jfrog.build.api.Agent;
import org.jfrog.build.api.Artifact;
import org.jfrog.build.api.Build;
import org.jfrog.build.api.BuildInfoFields;
import org.jfrog.build.api.BuildInfoProperties;
import org.jfrog.build.api.BuildRetention;
import org.jfrog.build.api.BuildType;
import org.jfrog.build.api.LicenseControl;
import org.jfrog.build.api.builder.ArtifactBuilder;
import org.jfrog.build.api.builder.BuildInfoBuilder;
import org.jfrog.build.api.builder.ModuleBuilder;
import org.jfrog.build.api.builder.PromotionStatusBuilder;
import org.jfrog.build.api.release.Promotion;
import org.jfrog.build.api.util.FileChecksumCalculator;
import org.jfrog.build.client.DeployDetails;
import org.jfrog.hudson.action.ActionableHelper;
import org.jfrog.hudson.release.ReleaseAction;
import org.jfrog.hudson.util.BuildRetentionFactory;
import org.jfrog.hudson.util.ExtractorUtils;
import org.sonatype.inject.Nullable;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deploys artifacts to Artifactory. This class is used only when the Maven 3 extractor is not active.
 *
 * @author Yossi Shaul
 */
public class GenericBuildInfoHelper {
    private static final String SHA1 = "SHA1";
    private static final String MD5 = "MD5";

    private ArtifactoryGenericConfigurator configurator;
    private final AbstractBuild build;
    private final BuildListener listener;
    private final EnvVars env;

    public GenericBuildInfoHelper(ArtifactoryGenericConfigurator configurator, AbstractBuild build,
            BuildListener listener) throws IOException, InterruptedException {
        this.configurator = configurator;
        this.build = build;
        this.listener = listener;
        this.env = build.getEnvironment(listener);
    }

    public Set<DeployDetails> createDeployDetailsAndAddToBuildInfo(@Nullable Build buildInfo)
            throws IOException, InterruptedException, NoSuchAlgorithmException {
        FilePath workspace = build.getWorkspace();
        Multimap<String, FileSet> fileSetMap = buildTargetPathToFiles(workspace);
        Set<DeployDetails> details = Sets.newHashSet();
        for (Map.Entry<String, FileSet> entry : fileSetMap.entries()) {
            details.addAll(buildDeployDetailsFromFileSet(entry, workspace.getRemote()));
        }
        if(buildInfo != null) {
            List<Artifact> artifacts = convertDeployDetailsToArtifacts(details);
            ModuleBuilder moduleBuilder =
                    new ModuleBuilder().id(build.getDisplayName() + ":" + build.getNumber())
                            .artifacts(artifacts);
            buildInfo.setModules(Lists.newArrayList(moduleBuilder.build()));
        }
        return details;
    }

    private List<Artifact> convertDeployDetailsToArtifacts(Set<DeployDetails> details) {
        List<Artifact> result = Lists.newArrayList();
        for (DeployDetails detail : details) {
            String ext = FilenameUtils.getExtension(detail.getFile().getName());
            Artifact artifact = new ArtifactBuilder(detail.getFile().getName()).md5(detail.getMd5())
                    .sha1(detail.getSha1()).type(ext).build();
            result.add(artifact);
        }
        return result;
    }

    /*    public void deploy() throws IOException, InterruptedException, NoSuchAlgorithmException {
        listener.getLogger().println("Deploying artifacts to " + artifactoryServer.getUrl());

    }*/

    private Multimap<String, FileSet> buildTargetPathToFiles(FilePath workingDir)
            throws IOException, InterruptedException {
        final Multimap<String, FileSet> result = HashMultimap.create();
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
                    FileSet fileSet = Util.createFileSet(f, entry.getKey());
                    if (fileSet != null) {
                        listener.getLogger().println(
                                "For pattern: " + entry.getKey() + " " + fileSet.size() + " artifacts were found");
                        result.put(entry.getValue(), fileSet);
                    } else {
                        listener.getLogger().println("For pattern: " + entry.getKey() + " no artifacts were found");
                    }
                    return null;
                }
            });
        }
        return result;
    }

    private Set<DeployDetails> buildDeployDetailsFromFileSet(Map.Entry<String, FileSet> fileSetEntry, String rootDir)
            throws IOException, NoSuchAlgorithmException {
        Set<DeployDetails> result = Sets.newHashSet();
        String targetPath = fileSetEntry.getKey();
        Iterator<FileResource> iterator = fileSetEntry.getValue().iterator();
        while (iterator.hasNext()) {
            FileResource fileResource = iterator.next();
            File artifactFile = fileResource.getFile();
            String relativePath = artifactFile.getAbsolutePath();
            if (StringUtils.startsWith(relativePath, rootDir)) {
                relativePath = StringUtils.removeStart(artifactFile.getAbsolutePath(), rootDir);
            } else {
                File fileBaseDir = fileResource.getBaseDir();
                if (fileBaseDir != null) {
                    relativePath = StringUtils.removeStart(artifactFile.getAbsolutePath(),
                            fileBaseDir.getAbsolutePath());
                }
            }
            relativePath = FilenameUtils.separatorsToUnix(relativePath);
            relativePath = StringUtils.removeStart(relativePath, "/");
            String path = PublishedItemsHelper.calculateTargetPath(relativePath, targetPath, artifactFile.getName());
            path = StringUtils.replace(path, "//", "/");

            // calculate the sha1 checksum that is not given by Jenkins and add it to the deploy details
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

    public Build extractBuildInfo(AbstractBuild build) {
        BuildInfoBuilder builder = new BuildInfoBuilder(build.getParent().getDisplayName())
                .number(build.getNumber() + "").type(BuildType.GENERIC)
                .agent(new Agent("hudson", build.getHudsonVersion()));
        String buildUrl = ActionableHelper.getBuildUrl(build);
        if (StringUtils.isNotBlank(buildUrl)) {
            builder.url(buildUrl);
        }

        Calendar startedTimestamp = build.getTimestamp();
        builder.startedDate(startedTimestamp.getTime());

        long duration = System.currentTimeMillis() - startedTimestamp.getTimeInMillis();
        builder.durationMillis(duration);

        String artifactoryPrincipal = configurator.getArtifactoryServer().getResolvingCredentials().getUsername();
        if (StringUtils.isBlank(artifactoryPrincipal)) {
            artifactoryPrincipal = "";
        }
        builder.artifactoryPrincipal(artifactoryPrincipal);

        String userCause = ActionableHelper.getUserCausePrincipal(build);
        if (userCause != null) {
            builder.principal(userCause);
        }

        Cause.UpstreamCause parent = ActionableHelper.getUpstreamCause(build);
        if (parent != null) {
            String parentProject = parent.getUpstreamProject();
            int buildNumber = parent.getUpstreamBuild();
            builder.parentName(parentProject);
            builder.parentNumber(buildNumber + "");
            if (StringUtils.isBlank(userCause)) {
                builder.principal("auto");
            }
        }

        gatherSysPropInfo(builder);
        addBuildInfoVariables(builder);

        String revision = ExtractorUtils.getVcsRevision(env);
        if (StringUtils.isNotBlank(revision)) {
            builder.vcsRevision(revision);
        }
        if (configurator.isIncludeEnvVars()) {
            for (Map.Entry<String, String> entry : env.entrySet()) {
                builder.addProperty(BuildInfoProperties.BUILD_INFO_ENVIRONMENT_PREFIX + entry.getKey(),
                        entry.getValue());
            }
        } else {
            MapDifference<String, String> difference = Maps.difference(env, System.getenv());
            Map<String, String> filteredEnvVars = difference.entriesOnlyOnLeft();
            for (Map.Entry<String, String> entry : filteredEnvVars.entrySet()) {
                builder.addProperty(BuildInfoProperties.BUILD_INFO_ENVIRONMENT_PREFIX + entry.getKey(),
                        entry.getValue());
            }
        }

        LicenseControl licenseControl = new LicenseControl(configurator.isRunChecks());
        if (configurator.isRunChecks()) {
            if (StringUtils.isNotBlank(configurator.getViolationRecipients())) {
                licenseControl.setLicenseViolationsRecipientsList(configurator.getViolationRecipients());
            }
            if (StringUtils.isNotBlank(configurator.getScopes())) {
                licenseControl.setScopesList(configurator.getScopes());
            }
        }
        licenseControl.setIncludePublishedArtifacts(configurator.isIncludePublishArtifacts());
        licenseControl.setAutoDiscover(configurator.isLicenseAutoDiscovery());
        builder.licenseControl(licenseControl);
        BuildRetention buildRetention = new BuildRetention(configurator.isDiscardBuildArtifacts());
        if (configurator.isDiscardOldBuilds()) {
            buildRetention = BuildRetentionFactory.createBuildRetention(build, configurator.isDiscardBuildArtifacts());
        }
        builder.buildRetention(buildRetention);

        // add staging status if it is a release build
        ReleaseAction release = ActionableHelper.getLatestAction(build, ReleaseAction.class);
        if (release != null) {
            String stagingRepoKey = release.getStagingRepositoryKey();
            if (StringUtils.isBlank(stagingRepoKey)) {
                stagingRepoKey = configurator.getRepositoryKey();
            }
            builder.addStatus(new PromotionStatusBuilder(Promotion.STAGED)
                    .timestampDate(startedTimestamp.getTime())
                    .comment(release.getStagingComment())
                    .repository(stagingRepoKey)
                    .ciUser(userCause).user(artifactoryPrincipal).build());
        }

        return builder.build();
    }

    private void gatherSysPropInfo(BuildInfoBuilder infoBuilder) {
        infoBuilder.addProperty("os.arch", System.getProperty("os.arch"));
        infoBuilder.addProperty("os.name", System.getProperty("os.name"));
        infoBuilder.addProperty("os.version", System.getProperty("os.version"));
        infoBuilder.addProperty("java.version", System.getProperty("java.version"));
        infoBuilder.addProperty("java.vm.info", System.getProperty("java.vm.info"));
        infoBuilder.addProperty("java.vm.name", System.getProperty("java.vm.name"));
        infoBuilder.addProperty("java.vm.specification.name", System.getProperty("java.vm.specification.name"));
        infoBuilder.addProperty("java.vm.vendor", System.getProperty("java.vm.vendor"));
    }

    private void addBuildInfoVariables(BuildInfoBuilder infoBuilder) {
        Map<String, String> buildVariables = build.getBuildVariables();
        for (Map.Entry<String, String> entry : buildVariables.entrySet()) {
            infoBuilder
                    .addProperty(BuildInfoProperties.BUILD_INFO_ENVIRONMENT_PREFIX + entry.getKey(), entry.getValue());
        }
    }
}
