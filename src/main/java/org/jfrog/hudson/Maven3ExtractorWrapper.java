/*
 * Copyright (C) 2011 JFrog Ltd.
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

package org.jfrog.hudson;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.maven.MavenModuleSet;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Environment;
import hudson.model.Result;
import hudson.model.listeners.RunListener;
import hudson.remoting.Which;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.jfrog.build.extractor.maven.BuildInfoRecorder;
import org.jfrog.hudson.util.BuildContext;
import org.jfrog.hudson.util.ExtractorUtils;
import org.jfrog.hudson.util.PluginDependencyHelper;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Map;

/**
 * @author Tomer Cohen
 */
@Extension
public class Maven3ExtractorWrapper extends RunListener<AbstractBuild> {

    @Override
    public Environment setUpEnvironment(final AbstractBuild build, Launcher launcher, final BuildListener listener)
            throws IOException, InterruptedException {
        // if not a native maven project return empty env.
        if (!(build.getProject() instanceof MavenModuleSet)) {
            return new Environment() {
            };
        }
        final MavenModuleSet project = (MavenModuleSet) build.getProject();
        // if archiving is enabled return empty env.
        if (!project.isArchivingDisabled()) {
            return new Environment() {
            };
        }
        final ArtifactoryRedeployPublisher publisher = project.getPublishers().get(ArtifactoryRedeployPublisher.class);
        // if the artifactory publisher is not active, return empty env.
        if (publisher == null) {
            return new Environment() {
            };
        }
        // create build context from existing publisher
        final BuildContext context = createBuildContextFromPublisher(publisher);
        // save the original maven opts to set after build is complete.
        final String originalMavenOpts = project.getMavenOpts();
        // set new maven opts with the location if the extractor
        project.setMavenOpts(appendNewMavenOpts(project, build));

        final File classWorldsFile = File.createTempFile("classworlds", "conf");
        URL resource = getClass().getClassLoader().getResource("org/jfrog/hudson/maven3/classworlds-native.conf");
        final String classworldsConfPath = ExtractorUtils.copyClassWorldsFile(build, resource, classWorldsFile);
        build.setResult(Result.SUCCESS);
        return new Environment() {
            @Override
            public void buildEnvVars(Map<String, String> env) {
                try {
                    ExtractorUtils.addBuilderInfoArguments(env, build, publisher.getArtifactoryServer(), context);
                    ExtractorUtils.addCustomClassworlds(env, classworldsConfPath);
                    env.put(ExtractorUtils.EXTRACTOR_USED, "true");
                } catch (Exception e) {
                    listener.getLogger().
                            format("Failed to collect Artifactory Build Info to properties file: %s", e.getMessage()).
                            println();
                    build.setResult(Result.FAILURE);
                    throw new RuntimeException(e);
                }
            }

            @Override
            public boolean tearDown(AbstractBuild build, BuildListener listener)
                    throws IOException, InterruptedException {
                project.setMavenOpts(originalMavenOpts);
                if (!context.isSkipBuildInfoDeploy() && build.getResult().isBetterOrEqualTo(Result.SUCCESS)) {
                    build.getActions().add(0, new BuildInfoResultAction(context.getArtifactoryName(), build));
                }
                FileUtils.deleteQuietly(classWorldsFile);
                return true;
            }
        };
    }

    private BuildContext createBuildContextFromPublisher(ArtifactoryRedeployPublisher publisher) {
        BuildContext context = new BuildContext(publisher.getDetails(), publisher, publisher.isRunChecks(),
                publisher.isIncludePublishArtifacts(), publisher.getViolationRecipients(), publisher.getScopes(),
                publisher.isLicenseAutoDiscovery(), publisher.isDiscardOldBuilds(), publisher.isDeployArtifacts(),
                publisher.getArtifactDeploymentPatterns(), publisher.isSkipBuildInfoDeploy(),
                publisher.isIncludeEnvVars(), publisher.isDiscardBuildArtifacts(), publisher.getMatrixParams());
        context.setEvenIfUnstable(publisher.isEvenIfUnstable());
        return context;
    }

    private String appendNewMavenOpts(MavenModuleSet project, AbstractBuild build) throws IOException {
        StringBuilder mavenOpts = new StringBuilder();
        String opts = project.getMavenOpts();
        if (StringUtils.isNotBlank(opts)) {
            mavenOpts.append(opts);
        }
        if (StringUtils.contains(mavenOpts.toString(), "-Dm3plugin.lib")) {
            return mavenOpts.toString();
        }
        File maven3ExtractorJar = Which.jarFile(BuildInfoRecorder.class);
        try {
            FilePath actualDependencyDirectory =
                    PluginDependencyHelper.getActualDependencyDirectory(build, maven3ExtractorJar);
            mavenOpts.append(" -Dm3plugin.lib=").append(actualDependencyDirectory.getRemote());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return mavenOpts.toString();
    }
}
