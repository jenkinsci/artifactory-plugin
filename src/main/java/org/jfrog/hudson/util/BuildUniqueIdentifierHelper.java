package org.jfrog.hudson.util;

import com.google.common.collect.Lists;
import hudson.Util;
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.model.AbstractProject;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import org.apache.commons.lang.StringUtils;
import org.jfrog.build.client.DeployDetails;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Utility class to help extracting and assembling parameters for the a unique build identifier.
 *
 * @author Tomer Cohen
 */
public class BuildUniqueIdentifierHelper {
    public static final String BUILD_PARENT_PROPERTY_PREFIX = "build.parent.";
    private static final String BUILD_PARENT_PROPERTY =
            BUILD_PARENT_PROPERTY_PREFIX + "${JOB_NAME}=${PARENT_BUILD_${JOB_NAME}}";
    private static final String BUILD_DOWNSTREAM_PROPERTY = "${BUILD_NUMBER}-${BUILD_ID}";

    private BuildUniqueIdentifierHelper() {
    }

    public static void addUniqueBuildIdentifier(DeployDetails.Builder builder, Map<String, String> env) {
        String[] propertySplit = getUniqueBuildIdentifier(env);
        builder.addProperty(propertySplit[0], propertySplit[1]);
    }

    public static void addUpstreamIdentifiers(DeployDetails.Builder builder, MavenModuleSetBuild build) {
        Map<JobPropertyDescriptor, JobProperty<? super MavenModuleSet>> jobProperties =
                build.getProject().getProperties();
        for (JobProperty<? super MavenModuleSet> property : jobProperties.values()) {
            if (property instanceof ParametersDefinitionProperty) {
                List<ParameterDefinition> definitions =
                        ((ParametersDefinitionProperty) property).getParameterDefinitions();
                for (ParameterDefinition definition : definitions) {
                    if (definition.getName().startsWith("PARENT_BUILD_")) {
                        StringParameterValue value = (StringParameterValue) definition.getDefaultParameterValue();
                        builder.addProperty(definition.getName(), value.value);
                    }
                }
            }
        }
    }

    public static void addDownstreamUniqueIdentifierAbstract(AbstractProject currentProject,
            AbstractProject childProject, Map<String, String> env) throws IOException {
        List<ParameterDefinition> result = Lists.newArrayList();
        Map<JobPropertyDescriptor, JobProperty<? super MavenModuleSet>> jobProperties = currentProject.getProperties();
        for (JobProperty<? super MavenModuleSet> property : jobProperties.values()) {
            if (property instanceof ParametersDefinitionProperty) {
                List<ParameterDefinition> definitions =
                        ((ParametersDefinitionProperty) property).getParameterDefinitions();
                for (ParameterDefinition definition : definitions) {
                    if (definition.getName().startsWith("PARENT_BUILD_")) {
                        result.add(definition);
                    }
                }
            }
        }
        String currentBuild = Util.replaceMacro(BUILD_PARENT_PROPERTY, env);
        String key = currentBuild.split("=")[1];
        key = stripPlaceHolder(key);
        String downstreamBuild = Util.replaceMacro(BUILD_DOWNSTREAM_PROPERTY, env);
        StringParameterDefinition definition = new StringParameterDefinition(key, downstreamBuild);
        result.add(definition);
        childProject.addProperty(new ParametersDefinitionProperty(result));
    }

    public static String[] getUniqueBuildIdentifier(Map<String, String> env) {
        String propertyString = Util.replaceMacro(BUILD_PARENT_PROPERTY, env);
        propertyString = stripPlaceHolder(propertyString);
        String[] split = propertyString.split("=");
        return new String[]{split[0], stripPlaceHolder(split[1])};
    }

    private static String stripPlaceHolder(String prop) {
        prop = StringUtils.removeStart(prop, "${");
        prop = StringUtils.removeEnd(prop, "}");
        return prop;
    }
}
