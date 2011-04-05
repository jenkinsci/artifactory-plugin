package org.jfrog.hudson.util;

import com.google.common.collect.Lists;
import hudson.Util;
import hudson.maven.MavenModuleSetBuild;
import hudson.model.*;
import org.apache.commons.lang.StringUtils;
import org.jfrog.build.api.ArtifactoryResolutionProperties;
import org.jfrog.build.client.DeployDetails;
import org.jfrog.hudson.action.ActionableHelper;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.jfrog.build.api.ArtifactoryResolutionProperties.ARTIFACTORY_BUILD_ROOT_MATRIX_PARAM_KEY;

/**
 * Utility class to help extracting and assembling parameters for the a unique build identifier.
 *
 * @author Tomer Cohen
 */
public class BuildUniqueIdentifierHelper {
    private static final String BUILD_ID_PROPERTY = "${JOB_NAME}-${BUILD_NUMBER}";
    private static final String UNIQUE_ARTIFACT_BUILD_ROOT_KEY =
            ArtifactoryResolutionProperties.ARTIFACT_BUILD_ROOT_KEY + "-" + "${JOB_NAME}-${BUILD_NUMBER}";

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
        String identifier = getUpstreamIdentifier(build);
        if (StringUtils.isNotBlank(identifier)) {
            builder.addProperty(ARTIFACTORY_BUILD_ROOT_MATRIX_PARAM_KEY, identifier);
        }
    }

    /**
     * Get the upstream identifier with {@link ArtifactoryResolutionProperties#ARTIFACT_BUILD_ROOT_KEY} as its key
     *
     * @param build The build from which to extract the unique identifier from.
     * @return The build's unique identifier with {@link ArtifactoryResolutionProperties#ARTIFACT_BUILD_ROOT_KEY} as its
     *         key
     */
    private static String getUpstreamIdentifier(AbstractBuild<?, ?> build) {
        ParametersDefinitionProperty definitionProperty =
                build.getProject().getProperty(ParametersDefinitionProperty.class);
        if (definitionProperty != null) {
            Cause.UpstreamCause upstreamCause = ActionableHelper.getUpstreamCause(build);
            ParameterDefinition parameterDefinition =
                    definitionProperty.getParameterDefinition(getUniqueIdentifierForUpstreamBuild(upstreamCause));
            if (parameterDefinition != null) {
                StringParameterValue value = (StringParameterValue) parameterDefinition.getDefaultParameterValue();
                return value.value;
            }
        }
        return null;
    }

    /**
     * Remove the unique build identifier from a project, such that it will not be a parametrised project anymore and
     * after the build has succeeded there is not meaning for it anymore.
     *
     * @param build The build from which to remove the {@link ParametersDefinitionProperty} which has a definition with
     *              key {@link ArtifactoryResolutionProperties#ARTIFACT_BUILD_ROOT_KEY}
     */
    public static void removeUniqueIdentifierFromProject(AbstractBuild<?, ?> build) throws IOException {
        AbstractProject<?, ?> project = build.getProject();
        ParametersDefinitionProperty property = project.getProperty(ParametersDefinitionProperty.class);
        if (property != null) {
            Cause.UpstreamCause upstreamCause = ActionableHelper.getUpstreamCause(build);
            ParameterDefinition definition =
                    property.getParameterDefinition(getUniqueIdentifierForUpstreamBuild(upstreamCause));
            if (definition != null) {
                List<ParameterDefinition> newDefinitions = Lists.newArrayList(property.getParameterDefinitions());
                newDefinitions.remove(definition);
                project.removeProperty(property);
                if (!newDefinitions.isEmpty()) {
                    project.addProperty(new ParametersDefinitionProperty(newDefinitions));
                }
            }
        }
    }

    /**
     * Add the unique identifier with {@link org.jfrog.build.api.ArtifactoryResolutionProperties#ARTIFACT_BUILD_ROOT_KEY}
     * as its key to all the child projects of the current build.
     *
     * @param build   The current build.
     * @param envVars The build's environment variables.
     */
    public static void addUniqueIdentifierToChildProjects(AbstractBuild build, Map<String, String> envVars)
            throws IOException {
        DependencyGraph graph = Hudson.getInstance().getDependencyGraph();
        List<DependencyGraph.Dependency> downstreamDependencies =
                graph.getDownstreamDependencies(build.getProject());
        for (DependencyGraph.Dependency dependency : downstreamDependencies) {
            AbstractProject project = dependency.getDownstreamProject();
            addDownstreamUniqueIdentifier(build, project, envVars);
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
    private static void addDownstreamUniqueIdentifier(AbstractBuild<?, ?> currentBuild,
            AbstractProject<?, ?> childProject, Map<String, String> env) throws IOException {
        // if the current project is not the root project, simply pull the property from the upstream projects
        // with the ID to pass it downwards
        if (ActionableHelper.getUpstreamCause(currentBuild) != null) {
            ParametersDefinitionProperty jobProperty =
                    currentBuild.getProject().getProperty(ParametersDefinitionProperty.class);
            if (jobProperty != null) {
                ParameterDefinition parameterDefinition =
                        jobProperty.getParameterDefinition(UNIQUE_ARTIFACT_BUILD_ROOT_KEY);
                if (parameterDefinition != null) {
                    ParametersDefinitionProperty property =
                            childProject.getProperty(ParametersDefinitionProperty.class);
                    if (property != null) {
                        ParameterDefinition childDefinition =
                                property.getParameterDefinition(UNIQUE_ARTIFACT_BUILD_ROOT_KEY);
                        if (parameterDefinition instanceof StringParameterDefinition) {
                            String value =
                                    ((StringParameterDefinition) parameterDefinition).getDefaultParameterValue().value;
                            ((StringParameterDefinition) childDefinition).setDefaultValue(value);
                        }
                    } else {
                        if (parameterDefinition instanceof StringParameterDefinition) {
                            String value =
                                    ((StringParameterDefinition) parameterDefinition).getDefaultParameterValue().value;
                            StringParameterDefinition newDefinition =
                                    new StringParameterDefinition(UNIQUE_ARTIFACT_BUILD_ROOT_KEY, value);
                            childProject.addProperty(new ParametersDefinitionProperty(newDefinition));
                        }
                    }
                }
            }
            // if it is the root project, add it as the unique identifier.
        } else {
            String downstreamBuild = Util.replaceMacro(BUILD_ID_PROPERTY, env);
            String downstreamReleaseKey = Util.replaceMacro(UNIQUE_ARTIFACT_BUILD_ROOT_KEY, env);
            ParametersDefinitionProperty property = childProject.getProperty(ParametersDefinitionProperty.class);
            if (property != null) {
                ParameterDefinition definition = property.getParameterDefinition(downstreamReleaseKey);
                if (definition == null) {
                    childProject.removeProperty(property);
                    List<ParameterDefinition> definitions = Lists.newArrayList(property.getParameterDefinitions());
                    definitions.add(new StringParameterDefinition(downstreamReleaseKey, downstreamBuild));
                    childProject.addProperty(new ParametersDefinitionProperty(definitions));
                } else if (definition instanceof StringParameterDefinition) {
                    ((StringParameterDefinition) definition).setDefaultValue(downstreamBuild);
                }
            } else {
                StringParameterDefinition definition =
                        new StringParameterDefinition(downstreamReleaseKey, downstreamBuild);
                childProject.addProperty(new ParametersDefinitionProperty(definition));
            }
        }
    }

    /**
     * @return The interpolated value of {@link BuildUniqueIdentifierHelper#BUILD_ID_PROPERTY} by the environment map.
     */
    public static String getUniqueBuildIdentifier(Map<String, String> env) {
        return Util.replaceMacro(BUILD_ID_PROPERTY, env);
    }

    /**
     * Get the unique identifier for the upstream build which tirggered the current build according to its upstream
     * cause. if there is no such cause, an {@link StringUtils#EMPTY} is returned.
     *
     * @param cause The upstream build cause of the current build, may be null.
     * @return The unique identifier of the upstream build, which is composed of: {@link
     *         ArtifactoryResolutionProperties#ARTIFACT_BUILD_ROOT_KEY}-{@link hudson.model.Cause.UpstreamCause#upstreamProject}-{@link
     *         hudson.model.Cause.UpstreamCause#upstreamBuild}
     */
    private static String getUniqueIdentifierForUpstreamBuild(Cause.UpstreamCause cause) {
        if (cause == null) {
            return StringUtils.EMPTY;
        }
        return ArtifactoryResolutionProperties.ARTIFACT_BUILD_ROOT_KEY + "-" + cause.getUpstreamProject() +
                "-" + cause.getUpstreamBuild();
    }
}
