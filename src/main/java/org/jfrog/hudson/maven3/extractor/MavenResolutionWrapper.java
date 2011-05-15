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

package org.jfrog.hudson.maven3.extractor;

import hudson.FilePath;
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.tasks.BuildWrapper;
import org.jfrog.build.api.util.NullLog;
import org.jfrog.build.client.ArtifactoryClientConfiguration;
import org.jfrog.hudson.ArtifactoryServer;
import org.jfrog.hudson.util.Credentials;
import org.jfrog.hudson.util.ExtractorUtils;

import java.io.IOException;
import java.net.URL;
import java.util.Map;

/**
 * @author Tomer Cohen
 */
public class MavenResolutionWrapper extends BuildWrapper {
    public class MavenResolutionEnvironment extends Environment {
        private final ArtifactoryServer artifactoryServer;
        private final Credentials preferredResolver;
        private final String downloadRepoKey;
        private final MavenModuleSetBuild build;
        private final String originalMavenOpts;
        private final URL resource;
        private FilePath classworldsConf;
        private boolean setup;

        public MavenResolutionEnvironment(ArtifactoryServer artifactoryServer, Credentials preferredResolver,
                String downloadRepoKey, MavenModuleSetBuild build) throws IOException {
            this.artifactoryServer = artifactoryServer;
            this.preferredResolver = preferredResolver;
            this.downloadRepoKey = downloadRepoKey;
            this.build = build;
            this.originalMavenOpts = build.getProject().getMavenOpts();
            this.resource = getClass().getClassLoader().getResource("org/jfrog/hudson/maven3/classworlds-native.conf");
        }

        @Override
        public void buildEnvVars(Map<String, String> env) {
            super.buildEnvVars(env);
            if (setup) {
                return;
            }
            ArtifactoryClientConfiguration configuration = new ArtifactoryClientConfiguration(new NullLog());
            configuration.setContextUrl(artifactoryServer.getUrl());
            configuration.resolver.setRepoKey(downloadRepoKey);
            configuration.resolver.setUsername(preferredResolver.getUsername());
            configuration.resolver.setPassword(preferredResolver.getPassword());
            if (!env.containsKey(ExtractorUtils.CLASSWORLDS_CONF_KEY)) {
                classworldsConf = ExtractorUtils.copyClassWorldsFile(build, resource);
                ExtractorUtils.addCustomClassworlds(env, classworldsConf.getRemote());
            }
            setNewMavenOpts();
            ExtractorUtils.addBuildRootIfNeeded(build, configuration);
            env.putAll(configuration.getAllProperties());
            setup = true;
        }

        @Override
        public boolean tearDown(AbstractBuild build, BuildListener listener) throws IOException, InterruptedException {
            final MavenModuleSet project = (MavenModuleSet) build.getProject();
            project.setMavenOpts(originalMavenOpts);
            if (classworldsConf != null) {
                classworldsConf.delete();
            }
            return super.tearDown(build, listener);
        }

        private void setNewMavenOpts() {
            try {
                build.getProject().setMavenOpts(ExtractorUtils.appendNewMavenOpts(build.getProject(), build));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
