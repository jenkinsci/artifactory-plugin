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

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import org.apache.commons.io.FilenameUtils;
import org.jfrog.build.api.Artifact;
import org.jfrog.build.api.Build;
import org.jfrog.build.api.BuildType;
import org.jfrog.build.api.Dependency;
import org.jfrog.build.api.builder.ArtifactBuilder;
import org.jfrog.build.api.builder.ModuleBuilder;
import org.jfrog.build.api.builder.dependency.BuildDependencyBuilder;
import org.jfrog.build.api.dependency.BuildDependency;
import org.jfrog.build.api.dependency.UserBuildDependency;
import org.jfrog.build.client.ArtifactoryBuildInfoClient;
import org.jfrog.build.client.DeployDetails;
import org.jfrog.hudson.AbstractBuildInfoDeployer;
import org.jfrog.hudson.util.ExtractorUtils;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Set;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Lists.transform;
import static com.google.common.collect.Sets.newHashSet;

/**
 * Builds the build info for generic deployment
 *
 * @author Yossi Shaul
 */
public class GenericBuildInfoDeployer extends AbstractBuildInfoDeployer {

    private ArtifactoryGenericConfigurator configurator;
    private final AbstractBuild build;
    private Build buildInfo;

    public GenericBuildInfoDeployer(ArtifactoryGenericConfigurator configurator, ArtifactoryBuildInfoClient client,
            AbstractBuild build, BuildListener listener, Set<DeployDetails> deployedArtifacts,
            List<UserBuildDependency> buildDependencies, List<Dependency> publishedDependencies)
            throws IOException, NoSuchAlgorithmException, InterruptedException {
        super(configurator, build, listener, client);
        this.configurator = configurator;
        this.build = build;
        this.buildInfo = createBuildInfo("Generic", "Generic", BuildType.GENERIC);
        createDeployDetailsAndAddToBuildInfo(deployedArtifacts, publishedDependencies);
        addBuildDependencies(buildDependencies);
    }

    public void deploy() throws IOException {
        String url = configurator.getArtifactoryServer().getUrl() + "/api/build";
        listener.getLogger().println("Deploying build info to: " + url);
        client.sendBuildInfo(buildInfo);
    }

    private void addBuildDependencies(List<UserBuildDependency> buildDependencies) {
        buildInfo.setBuildDependencies(transform(newArrayList(newHashSet(buildDependencies)),
                new Function<UserBuildDependency, BuildDependency>() {
                    public org.jfrog.build.api.dependency.BuildDependency apply(UserBuildDependency dependencyUser) {
                        final String buildNumber = dependencyUser.getBuildNumberResponse();
                        return buildNumber == null ? null
                                //Build number is null for unresolved dependencies (wrong build name or build number).
                                : new BuildDependencyBuilder().
                                name(dependencyUser.getBuildName()).
                                number(buildNumber).
                                url(dependencyUser.getBuildUrl()).
                                started(dependencyUser.getBuildStarted()).
                                build();
                    }
                }));
    }

    private void createDeployDetailsAndAddToBuildInfo(Set<DeployDetails> deployedArtifacts,
            List<Dependency> publishedDependencies)
            throws IOException, NoSuchAlgorithmException {
        List<Artifact> artifacts = convertDeployDetailsToArtifacts(deployedArtifacts);
        ModuleBuilder moduleBuilder =
                new ModuleBuilder().id(
                        ExtractorUtils.sanitizeBuildName(build.getParent().getDisplayName()) + ":" + build.getNumber())
                        .artifacts(artifacts);
        moduleBuilder.dependencies(publishedDependencies);
        buildInfo.setModules(Lists.newArrayList(moduleBuilder.build()));
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
}
