package org.jfrog.hudson;

/**
 * @author Lior Hasson
 */
public interface MultiConfigurationAware {
    String getArtifactoryCombinationFilter();

    boolean isMultiConfProject();
}
