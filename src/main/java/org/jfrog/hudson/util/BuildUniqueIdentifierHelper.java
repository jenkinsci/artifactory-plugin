package org.jfrog.hudson.util;

import hudson.Util;
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import org.jfrog.build.client.DeployDetails;
import org.jfrog.hudson.action.ActionableHelper;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.jfrog.build.api.ArtifactoryResolutionProperties.ARTIFACTORY_BUILD_ROOT_MATRIX_PARAM_KEY;
import static org.jfrog.build.api.ArtifactoryResolutionProperties.ARTIFACT_BUILD_ROOT_KEY;

/**
 * Utility class to help extracting and assembling parameters for the a unique build identifier.
 *
 * @author Tomer Cohen
 */
public class BuildUniqueIdentifierHelper {
    private static final String BUILD_ID_PROPERTY = "${JOB_NAME}-${BUILD_NUMBER}";

    private BuildUniqueIdentifierHelper() {
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
    public static void addUpstreamIdentifier(DeployDetails.Builder builder, MavenModuleSetBuild build) {
        Map<JobPropertyDescriptor, JobProperty<? super MavenModuleSet>> jobProperties =
                build.getProject().getProperties();
        for (JobProperty<? super MavenModuleSet> property : jobProperties.values()) {
            if (property instanceof ParametersDefinitionProperty) {
                List<ParameterDefinition> definitions =
                        ((ParametersDefinitionProperty) property).getParameterDefinitions();
                for (ParameterDefinition definition : definitions) {
                    if (ARTIFACT_BUILD_ROOT_KEY.equals(definition.getName())) {
                        StringParameterValue value = (StringParameterValue) definition.getDefaultParameterValue();
                        builder.addProperty(ARTIFACTORY_BUILD_ROOT_MATRIX_PARAM_KEY, value.value);
                    }
                }
            }
        }
    }

    /**
     * Add a unique identifier to child project, the key of the parameter that is propogated downwards is: {@link
     * org.jfrog.build.api.ArtifactoryResolutionProperties#ARTIFACT_BUILD_ROOT_KEY}. If the current build is the root
     * build, then the value is the interpolated value of {@link BuildUniqueIdentifierHelper#BUILD_ID_PROPERTY}
     * otherwise it is the value that is taken from the upstream build.
     *
     * @param currentBuild The current build
     * @param childProject The child build
     * @param env          The environment used for interpolation of the unique identifier
     */
    public static void addDownstreamUniqueIdentifier(AbstractBuild currentBuild,
            AbstractProject childProject, Map<String, String> env) throws IOException {
        // if the current project is not the root project, simply pull the property from the upstream projects
        // with the ID to pass it downwards
        if (ActionableHelper.getUpstreamCause(currentBuild) != null) {
            Map<JobPropertyDescriptor, JobProperty<? super MavenModuleSet>> jobProperties =
                    currentBuild.getProject().getProperties();
            for (JobProperty<? super MavenModuleSet> property : jobProperties.values()) {
                if (property instanceof ParametersDefinitionProperty) {
                    List<ParameterDefinition> definitions =
                            ((ParametersDefinitionProperty) property).getParameterDefinitions();
                    for (ParameterDefinition definition : definitions) {
                        if (definition.getName().startsWith(ARTIFACT_BUILD_ROOT_KEY)) {
                            childProject.addProperty(new ParametersDefinitionProperty(definition));
                        }
                    }
                }
            }
            // if it is the root project, add it as the unique identifier.
        } else {
            String downstreamBuild = Util.replaceMacro(BUILD_ID_PROPERTY, env);
            StringParameterDefinition definition =
                    new StringParameterDefinition(ARTIFACT_BUILD_ROOT_KEY, downstreamBuild);
            childProject.addProperty(new ParametersDefinitionProperty(definition));
        }
    }

    /**
     * @return The interpolated value of {@link BuildUniqueIdentifierHelper#BUILD_ID_PROPERTY} by the environment map.
     */
    private static String getUniqueBuildIdentifier(Map<String, String> env) {
        return Util.replaceMacro(BUILD_ID_PROPERTY, env);
    }
}
