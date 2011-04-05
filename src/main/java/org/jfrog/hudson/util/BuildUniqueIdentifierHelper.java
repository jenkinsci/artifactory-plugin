package org.jfrog.hudson.util;

import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildableItemWithBuildWrappers;
import org.apache.commons.lang.StringUtils;
import org.jfrog.build.client.DeployDetails;
import org.jfrog.hudson.ArtifactoryRedeployPublisher;
import org.jfrog.hudson.action.ActionableHelper;
import org.jfrog.hudson.gradle.ArtifactoryGradleConfigurator;

import java.util.Map;

import static org.jfrog.build.api.ArtifactoryResolutionProperties.ARTIFACTORY_BUILD_ROOT_MATRIX_PARAM_KEY;

/**
 * Utility class to help extracting and assembling parameters for the a unique build identifier.
 *
 * @author Tomer Cohen
 */
public class BuildUniqueIdentifierHelper {
    private static final String BUILD_ID_PROPERTY = "${JOB_NAME}-${BUILD_NUMBER}";

    private BuildUniqueIdentifierHelper() {
        // utility class
        throw new IllegalAccessError();
    }

    /**
     * Add the unique build identifier as a matrix params to all artifacts that are being deployed as part of the build.
     * The key of the matrix param is {@link org.jfrog.build.api.ArtifactoryResolutionProperties#ARTIFACTORY_BUILD_ROOT_MATRIX_PARAM_KEY}
     * and the identifier value is the interpolated value of {@link BuildUniqueIdentifierHelper#BUILD_ID_PROPERTY}
     *
     * @param builder The deploy details builder
     * @param env     The map used for interpolation of the value.
     */
    public static void addUniqueBuildIdentifier(DeployDetails.Builder builder, Map<String, String> env) {
        String identifier = getUniqueBuildIdentifier(env);
        builder.addProperty(ARTIFACTORY_BUILD_ROOT_MATRIX_PARAM_KEY, identifier);
    }

    /**
     * Add the unique identifier as a matrix param with key {@link org.jfrog.build.api.ArtifactoryResolutionProperties#ARTIFACTORY_BUILD_ROOT_MATRIX_PARAM_KEY}
     * The value of this parameters is taken from the upstream build.
     *
     * @param builder The deploy details builder.
     * @param build   The upstream build from where to find the value of the property from.
     */
    public static void addUpstreamIdentifier(DeployDetails.Builder builder, AbstractBuild<?, ?> build) {
        String identifier = getUpstreamIdentifier(build);
        if (StringUtils.isNotBlank(identifier)) {
            builder.addProperty(ARTIFACTORY_BUILD_ROOT_MATRIX_PARAM_KEY, identifier);
        }
    }

    /**
     * Check whether to pass the the downstream identifier according to the <b>root</b> build's descriptor
     *
     * @param build The current build
     * @return True if to pass the downstream identifier to the child projects.
     */
    public static boolean isPassIdentifiedDownstream(AbstractBuild<?, ?> build) {
        ArtifactoryRedeployPublisher publisher =
                ActionableHelper.getPublisher(build.getProject(), ArtifactoryRedeployPublisher.class);
        if (publisher != null) {
            return publisher.isPassIdentifiedDownstream();
        }
        ArtifactoryGradleConfigurator wrapper = ActionableHelper
                .getBuildWrapper((BuildableItemWithBuildWrappers) build.getProject(),
                        ArtifactoryGradleConfigurator.class);
        return wrapper != null && wrapper.isPassIdentifiedDownstream();
    }

    /**
     * Get the identifier from the <b>root</b> build. which is composed of {@link hudson.model.AbstractItem#getName()}-{@link
     * hudson.model.Run#getNumber()}
     *
     * @param rootBuild The root build
     * @return The upstream identifier.
     */
    public static String getUpstreamIdentifier(AbstractBuild<?, ?> rootBuild) {
        AbstractProject<?, ?> rootProject = rootBuild.getProject();
        return rootProject.getName() + "-" + rootBuild.getNumber();
    }

    /**
     * @return The interpolated value of {@link BuildUniqueIdentifierHelper#BUILD_ID_PROPERTY} by the environment map.
     */
    public static String getUniqueBuildIdentifier(Map<String, String> env) {
        return Util.replaceMacro(BUILD_ID_PROPERTY, env);
    }
}
