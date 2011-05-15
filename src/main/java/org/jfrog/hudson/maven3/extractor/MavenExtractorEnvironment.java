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
public class MavenExtractorEnvironment extends BaseMavenEnvironment {

    private final MavenModuleSet project;
    private final String originalMavenOpts;
    private final ArtifactoryRedeployPublisher publisher;
    private final BuildContext buildContext;
    private final AbstractBuild build;
    private final BuildListener buildListener;
    private final EnvVars envVars;
    private FilePath classworldsConf;
    private boolean setup;

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
        boolean isValid;
        try {
            isValid = MavenVersionHelper
                    .isAtLeastResolutionCapableVersion((MavenModuleSetBuild) build, envVars, buildListener);
        } catch (Exception e) {
            throw new RuntimeException(e);

        }
        // if not valid Maven version return empty environment
        if (!isValid) {
            return;
        }
        env.put(ExtractorUtils.EXTRACTOR_USED, "true");
        if (setup) {
            return;
        }
        try {
            URL resource = getClass().getClassLoader().getResource("org/jfrog/hudson/maven3/classworlds-native.conf");
            if (!env.containsKey(ExtractorUtils.CLASSWORLDS_CONF_KEY)) {
                classworldsConf = ExtractorUtils.copyClassWorldsFile(build, resource);
                ExtractorUtils.addCustomClassworlds(env, classworldsConf.getRemote());
            }
            project.setMavenOpts(appendNewMavenOpts(project, build));
            ExtractorUtils.addBuilderInfoArguments(env, build, publisher.getArtifactoryServer(), buildContext);
            setup = true;
        } catch (Exception e) {
            throw new RuntimeException(e);
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
