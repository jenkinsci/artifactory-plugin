package org.jfrog.hudson.util;

import hudson.maven.MavenBuild;
import hudson.maven.reporters.MavenArtifactRecord;

import java.util.List;

/**
 * @author Yossi Shaul
 */
public class ActionableHelper {

    public static MavenArtifactRecord getLatestMavenArtifactRecord(MavenBuild mavenBuild) {
        // one module may produce multiple MavenArtifactRecord entries, the last one contains all the info we need
        // (previous ones might only contain partial information, eg, only main artifact)
        List<MavenArtifactRecord> records = mavenBuild.getActions(MavenArtifactRecord.class);
        return records.get(records.size() - 1);
    }
}
