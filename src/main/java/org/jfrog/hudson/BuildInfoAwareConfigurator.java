package org.jfrog.hudson;

/**
 * Represents a class that can be passed to {@link AbstractBuildInfoDeployer} for build info creation
 *
 * @author Shay Yaakov
 */
public interface BuildInfoAwareConfigurator {

    ArtifactoryServer getArtifactoryServer();

    String getRepositoryKey();

    boolean isIncludeEnvVars();

    boolean isRunChecks();

    String getViolationRecipients();

    boolean isIncludePublishArtifacts();

    String getScopes();

    boolean isLicenseAutoDiscovery();

    boolean isDiscardOldBuilds();

    boolean isDiscardBuildArtifacts();
}
