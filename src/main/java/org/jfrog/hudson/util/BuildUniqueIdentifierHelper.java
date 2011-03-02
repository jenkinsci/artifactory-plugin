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

    public static void addUniqueBuildIdentifier(DeployDetails.Builder builder, Map<String, String> env) {
        String identifier = getUniqueBuildIdentifier(env);
        builder.addProperty(ARTIFACTORY_BUILD_ROOT_MATRIX_PARAM_KEY, identifier);
    }

    public static void addUpstreamIdentifiers(DeployDetails.Builder builder, MavenModuleSetBuild build) {
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

    public static String getUniqueBuildIdentifier(Map<String, String> env) {
        return Util.replaceMacro(BUILD_ID_PROPERTY, env);
    }
}
