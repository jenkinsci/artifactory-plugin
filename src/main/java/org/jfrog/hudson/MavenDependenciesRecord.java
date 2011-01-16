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

package org.jfrog.hudson;

import hudson.maven.MavenBuild;
import hudson.model.Action;

import java.util.Set;

/**
 * Records dependencies (including transitive) of a maven module.
 *
 * @author Yossi Shaul
 */
public class MavenDependenciesRecord implements Action {
    private final MavenBuild build;
    private final Set<MavenDependency> dependencies;

    public MavenDependenciesRecord(MavenBuild build, Set<MavenDependency> dependencies) {
        this.build = build;
        this.dependencies = dependencies;
    }

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return null;
    }

    public String getUrlName() {
        return null;
    }

    public Set<MavenDependency> getDependencies() {
        return dependencies;
    }
}
