package org.jfrog.hudson.util.plugins;

import hudson.matrix.Combination;
import hudson.matrix.MatrixConfiguration;
import hudson.model.AbstractBuild;
import org.jfrog.build.client.ArtifactoryClientConfiguration;

import java.util.Map;

/**
 * @author Lior Hasson
 */
public class MultiConfigurationUtils {
    public static boolean isfiltered(final AbstractBuild build, String combinationFilter) {
        if (build.getProject() instanceof MatrixConfiguration) {
            MatrixConfiguration matrixConf = ((MatrixConfiguration) build.getProject());
            return !matrixConf.getCombination().
                    evalGroovyExpression(matrixConf.getParent().getAxes(), combinationFilter);
        }

        return false;
    }

    public static void addMatrixCombination(AbstractBuild<?, ?> build, ArtifactoryClientConfiguration configuration) {
        if (build.getProject() instanceof MatrixConfiguration) {
            Combination combination = ((MatrixConfiguration) build.getProject()).getCombination();
            for (Map.Entry<String, String> entries : combination.entrySet()) {
                configuration.info.addRunParameters(entries.getKey(), entries.getValue());
            }
        }
    }
}
