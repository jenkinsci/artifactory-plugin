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
import hudson.model.AbstractBuild;
import hudson.model.Environment;
import hudson.remoting.Which;
import org.apache.commons.lang.StringUtils;
import org.jfrog.build.extractor.maven.BuildInfoRecorder;
import org.jfrog.hudson.util.PluginDependencyHelper;

import java.io.File;
import java.io.IOException;

/**
 * @author Tomer Cohen
 */
public abstract class BaseMavenEnvironment extends Environment {

    protected String appendNewMavenOpts(MavenModuleSet project, AbstractBuild build) throws IOException {
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
