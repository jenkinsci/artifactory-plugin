/*
 * Copyright (C) 2011 JFrog Ltd.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jfrog.hudson.util;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.io.Closeables;
import hudson.FilePath;
import org.apache.commons.lang.StringUtils;
import org.jfrog.hudson.release.gradle.GradleModule;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Utility class to help rewrite a properties file while keeping the contents of the properties file intact (e.g
 * comments)
 *
 * @author Tomer Cohen
 */
public class PropertyUtils {
    private static Logger debuggingLogger = Logger.getLogger(PropertyUtils.class.getName());

    private PropertyUtils() {
        // utility class
        throw new IllegalAccessError();
    }

    /**
     * Read the {@code gradle.properties} and parse it into {@link GradleModule}s. Only those keys which are given will
     * be included in the parsing.
     *
     * @param gradlePropPath The path of the gradle.properties file
     * @param propKeys       The property keys that should be taken into account when reading the properties file.
     * @return A map of {@link GradleModule}s that were assembled from the properties file, with the version as its
     *         value.
     * @throws IOException In case an error occurs while reading the properties file, this exception is thrown.
     */
    public static Map<GradleModule, String> getModulesPropertiesFromPropFile(FilePath gradlePropPath, String propKeys)
            throws IOException {
        Properties gradleProps = loadGradleProperties(gradlePropPath);
        ImmutableMap<String, String> gradlePropsMap = Maps.fromProperties(gradleProps);
        Map<GradleModule, String> versionsByPropName = Maps.newHashMap();
        String[] split;
        if (StringUtils.isBlank(propKeys)) {
            split = new String[0];
        } else {
            split = StringUtils.split(propKeys, ",");
        }
        for (Map.Entry<String, String> entry : gradlePropsMap.entrySet()) {
            for (String propKey : split) {
                if (propKey.equals(entry.getKey())) {
                    versionsByPropName.put(new GradleModule(entry.getKey(), entry.getValue()), entry.getValue());
                }
            }
        }
        return versionsByPropName;
    }

    /**
     * Load a properties file from a file path
     *
     * @param gradlePropertiesFilePath The file path where the gradle.properties is located.
     * @return The loaded properties.
     * @throws IOException In case an error occurs while reading the properties file, this exception is thrown.
     */
    private static Properties loadGradleProperties(FilePath gradlePropertiesFilePath) throws IOException {
        File gradlePropertiesFile = new File(gradlePropertiesFilePath.getRemote());
        Properties gradleProps = new Properties();
        if (gradlePropertiesFile.exists()) {
            debuggingLogger.fine("Gradle properties file exists at: " + gradlePropertiesFile.getAbsolutePath());
            FileInputStream stream = new FileInputStream(gradlePropertiesFile);
            try {
                gradleProps.load(stream);
            } finally {
                Closeables.closeQuietly(stream);
            }
        }
        return gradleProps;
    }
}
