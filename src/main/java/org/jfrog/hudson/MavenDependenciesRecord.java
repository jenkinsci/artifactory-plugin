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
