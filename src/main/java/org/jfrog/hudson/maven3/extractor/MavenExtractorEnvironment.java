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

import hudson.EnvVars;
import hudson.FilePath;
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Environment;
import org.jfrog.build.api.BuildInfoConfigProperties;
import org.jfrog.build.client.ArtifactoryClientConfiguration;
import org.jfrog.hudson.ArtifactoryRedeployPublisher;
import org.jfrog.hudson.util.BuildContext;
import org.jfrog.hudson.util.ExtractorUtils;
import org.jfrog.hudson.util.MavenVersionHelper;

import java.io.IOException;
import java.net.URL;
import java.util.Map;

/**
 * Class for setting up the {@link Environment} for a {@link MavenModuleSet} project. Responsible for adding the new
 * maven opts with the location of the plugin.
 *
 * @author Tomer Cohen
 */
public class MavenExtractorEnvironment extends Environment {

    private final MavenModuleSet project;
    private final String originalMavenOpts;
    private final ArtifactoryRedeployPublisher publisher;
    private final BuildContext buildContext;
    private final MavenModuleSetBuild build;
    private final BuildListener buildListener;
    private final EnvVars envVars;
    private FilePath classworldsConf;
    private String propertiesFilePath;

    // the build env vars method may be called again from another setUp of a wrapper so we need this flag to
    // attempt only once certain operations (like copying file or changing maven opts).
    private boolean initialized;

    public MavenExtractorEnvironment(MavenModuleSetBuild build, ArtifactoryRedeployPublisher publisher,
            BuildContext buildContext, BuildListener buildListener) throws IOException, InterruptedException {
        this.buildListener = buildListener;
        this.project = build.getProject();
        this.build = build;
        this.publisher = publisher;
        this.buildContext = buildContext;
        this.originalMavenOpts = project.getMavenOpts();
        this.envVars = build.getEnvironment(buildListener);
    }

    @Override
    public void buildEnvVars(Map<String, String> env) {
        // if not valid Maven version return empty environment
        if (!isMavenVersionValid()) {
            return;
        }
        env.put(ExtractorUtils.EXTRACTOR_USED, "true");

        if (classworldsConf == null && !env.containsKey(ExtractorUtils.CLASSWORLDS_CONF_KEY)) {
            URL resource = getClass().getClassLoader().getResource("org/jfrog/hudson/maven3/classworlds-native.conf");
            classworldsConf = ExtractorUtils.copyClassWorldsFile(build, resource);
        }

        if (classworldsConf != null) {
            ExtractorUtils.addCustomClassworlds(env, classworldsConf.getRemote());
        }

        if (!initialized) {
            try {
                build.getProject().setMavenOpts(ExtractorUtils.appendNewMavenOpts(project, build));
                ArtifactoryClientConfiguration configuration = ExtractorUtils.addBuilderInfoArguments(
                        env, build, publisher.getArtifactoryServer(), buildContext);
                propertiesFilePath = configuration.getPropertiesFile();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            initialized = true;
        }

        env.put(BuildInfoConfigProperties.PROP_PROPS_FILE, propertiesFilePath);
    }

    private boolean isMavenVersionValid() {
        try {
            return MavenVersionHelper.isAtLeastResolutionCapableVersion(build, envVars, buildListener);
        } catch (Exception e) {
            throw new RuntimeException("Unable to determine Maven version", e);
        }
    }

    @Override
    public boolean tearDown(AbstractBuild build, BuildListener listener) throws IOException, InterruptedException {
        project.setMavenOpts(originalMavenOpts);
        if (classworldsConf != null) {
            classworldsConf.delete();
        }
        return true;
    }
}
