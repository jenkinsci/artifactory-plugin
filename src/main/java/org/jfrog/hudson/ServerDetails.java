/*
 * Copyright (C) 2010 JFrog Ltd.
 *
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

package org.jfrog.hudson;

import com.google.common.collect.Maps;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import hudson.util.XStream2;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Artifacts resolution and deployment configuration.
 */
public class ServerDetails {
    public final String artifactoryName;
    /**
     * Artifactory server URL
     */
    private final String artifactoryUrl;
    /**
     * Configuration of the repository to deploy release artifacts to
     */
    private final RepositoryConf deployReleaseRepository;
    /**
     * Configuration of the repository to deploy snapshot artifacts to. If not specified will use the deployReleaseRepository
     */
    private final RepositoryConf deploySnapshotRepository;
    /**
     * Configuration of repository to use to download snapshots artifacts
     */
    private final RepositoryConf resolveSnapshotRepository;
    /**
     * Configuration of repository to use to download artifacts
     */
    private final RepositoryConf resolveReleaseRepository;
    /**
     /**
     * @deprecated: Use org.jfrog.hudson.ServerDetails#deployReleaseRepository
     */
    @Deprecated
    public String repositoryKey;
    /**
     * @deprecated: Use org.jfrog.hudson.ServerDetails#deploySnapshotRepository
     */
    @Deprecated
    public String snapshotsRepositoryKey;
    /**
     * @deprecated: Use org.jfrog.hudson.ServerDetails#resolveSnapshotRepository
     */
    @Deprecated
    public String downloadSnapshotRepositoryKey;
    /**
     * @deprecated: Use org.jfrog.hudson.ServerDetails#resolveReleaseRepository
     */
    @Deprecated
    public String downloadReleaseRepositoryKey;
    /**
     * Display name of repository to use to download snapshots artifacts
     */
    private String downloadSnapshotRepositoryDisplayName;
    /**
     * Display name of repository to use to download artifacts
     */
    private String downloadReleaseRepositoryDisplayName;
    private PluginSettings stagingPlugin;
    private String userPluginKey;
    private String userPluginParams;
    /**
     * @deprecated: Use org.jfrog.hudson.ServerDetails#downloadReleaseRepositoryKey
     */
    @Deprecated
    private String downloadRepositoryKey;

    @DataBoundConstructor
    public ServerDetails(String artifactoryName, String artifactoryUrl, RepositoryConf deployReleaseRepository,
                         RepositoryConf deploySnapshotRepository, RepositoryConf resolveReleaseRepository,
                         RepositoryConf resolveSnapshotRepository,
                         String userPluginKey, String userPluginParams) {
        this.artifactoryName = artifactoryName;
        this.artifactoryUrl = artifactoryUrl;
        this.deployReleaseRepository = deployReleaseRepository;
        this.deploySnapshotRepository = deploySnapshotRepository;
        this.resolveReleaseRepository = resolveReleaseRepository;
        this.resolveSnapshotRepository = resolveSnapshotRepository;
        this.userPluginKey = userPluginKey;
        this.userPluginParams = userPluginParams;
        createStagingPlugin();
    }

    public ServerDetails(String artifactoryName, String artifactoryUrl, RepositoryConf deployReleaseRepository, RepositoryConf deploySnapshotRepository,
                         RepositoryConf resolveReleaseRepository, RepositoryConf resolveSnapshotRepository) {
        this(artifactoryName, artifactoryUrl, deployReleaseRepository, deploySnapshotRepository, resolveReleaseRepository,
                resolveSnapshotRepository, null, null);
    }

    public RepositoryConf getDeployReleaseRepository() {
        return deployReleaseRepository;
    }

    public RepositoryConf getDeploySnapshotRepository() {
        if (deploySnapshotRepository == null) {
            return deployReleaseRepository;
        }
        return deploySnapshotRepository;
    }

    public RepositoryConf getResolveReleaseRepository() {
        return resolveReleaseRepository;
    }

    public RepositoryConf getResolveSnapshotRepository() {
        if (resolveSnapshotRepository == null) {
            return resolveReleaseRepository;
        }
        return resolveSnapshotRepository;
    }

    public String getUserPluginKey() {
        return stagingPlugin != null ? stagingPlugin.getPluginName() : null;
    }

    public String getDeployReleaseRepositoryKey() {
        if (deployReleaseRepository != null){
            return deployReleaseRepository.getRepoKey();
        }
        return StringUtils.EMPTY;
    }

    public String getDeploySnapshotRepositoryKey() {
        return getDeploySnapshotRepository().getRepoKey();
    }

    public String getResolveReleaseRepositoryKey() {
        return getResolveReleaseRepository().getRepoKey();
    }

    public String getResolveSnapshotRepositoryKey() {
        return getResolveSnapshotRepository().getRepoKey();
    }


    public void createStagingPlugin() {
        if (stagingPlugin == null) {
            stagingPlugin = new PluginSettings();
        }
        if (userPluginKey != null) {
            stagingPlugin.setPluginName(userPluginKey);
        }
        if (userPluginParams != null) {
            Map<String, String> paramsMap = Maps.newHashMap();
            String[] params = userPluginParams.split(" ");
            for (String param : params) {
                String[] keyValue = param.split("=");
                if (keyValue.length == 2) {
                    paramsMap.put(keyValue[0], keyValue[1]);
                }
            }
            stagingPlugin.setParamMap(paramsMap);
        }
    }

    public String getDownloadReleaseRepositoryDisplayName() {
        // The following if statement is for backward compatibility with version 2.2.3 and below of the plugin.
        // Without the below code, upgrade from 2.2.3 or below to 2.2.4 and above will cause the configuration to be lost.
        // This should be eventually removed.
        if (downloadReleaseRepositoryDisplayName == null && resolveReleaseRepository != null) {
            return resolveReleaseRepository.getRepoKey();
        }
        return downloadReleaseRepositoryDisplayName;
    }

    public String getDownloadSnapshotRepositoryDisplayName() {
        // The following if statement is for backward compatibility with version 2.2.3 and below of the plugin.
        // Without the below code, upgrade from 2.2.3 or below to 2.2.4 and above will cause the configuration to be lost.
        // This should be eventually removed.
        if (downloadSnapshotRepositoryDisplayName == null && resolveSnapshotRepository != null) {
            return resolveSnapshotRepository.getRepoKey();
        }
        return downloadSnapshotRepositoryDisplayName;
    }


    public PluginSettings getStagingPlugin() {
        return stagingPlugin;
    }

    public void setStagingPlugin(PluginSettings stagingPlugin) {
        this.stagingPlugin = stagingPlugin;
    }

    public String getArtifactoryUrl() {
        //support legacy code when artifactoryName was the url
        return artifactoryUrl != null ? artifactoryUrl : artifactoryName;
    }

    public String getStagingPluginName() {
        return (stagingPlugin != null) ? stagingPlugin.getPluginName() : null;
    }

    public String getPluginParamValue(String pluginName, String paramKey) {
        return (stagingPlugin != null) ? stagingPlugin.getPluginParamValue(pluginName, paramKey) : null;
    }

    public static final class ConverterImpl extends XStream2.PassthruConverter<ServerDetails> {
        // mapping of the old ServerDetails field to the corresponding new field
        private static final Map<String, String> newToOldFields;

        static {
            newToOldFields = new HashMap<String, String>();
            newToOldFields.put("repositoryKey", "deployReleaseRepository");
            newToOldFields.put("snapshotsRepositoryKey", "deploySnapshotRepository");
            newToOldFields.put("downloadSnapshotRepositoryKey", "resolveSnapshotRepository");
            newToOldFields.put("downloadReleaseRepositoryKey", "resolveReleaseRepository");
        }
        public ConverterImpl(XStream2 xstream) {
            super(xstream);
        }

        public void convertToReleaseAndSnapshotRepository(ServerDetails server) throws NoSuchFieldException, IllegalAccessException {
            Class<? extends ServerDetails> overrideClass = server.getClass();

            Field oldReleaseRepositoryField = overrideClass.getDeclaredField("downloadRepositoryKey");
            oldReleaseRepositoryField.setAccessible(true);
            Object oldReleaseRepositoryValue = oldReleaseRepositoryField.get(server);

            if (oldReleaseRepositoryValue != null && StringUtils.isNotBlank((String) oldReleaseRepositoryValue)) {
                Field newReleaseRepositoryField = overrideClass.getDeclaredField("downloadReleaseRepositoryKey");
                newReleaseRepositoryField.setAccessible(true);
                newReleaseRepositoryField.set(server, oldReleaseRepositoryValue);

                Field newSnapshotRepositoryField = overrideClass.getDeclaredField("downloadSnapshotRepositoryKey");
                newSnapshotRepositoryField.setAccessible(true);
                newSnapshotRepositoryField.set(server, oldReleaseRepositoryValue);
            }
        }

        public void convertToDynamicReposSelection(ServerDetails server) throws NoSuchFieldException, IllegalAccessException {
            Class<? extends ServerDetails> overrideClass = server.getClass();
            for (Map.Entry<String, String> e : newToOldFields.entrySet()) {
                setNewReposFieldFromOld(server, overrideClass, e.getKey(), e.getValue());
            }
        }

        private void setNewReposFieldFromOld(Object reflectedObject, Class classToChange, String oldFieldName,
                                             String newFieldName) throws NoSuchFieldException, IllegalAccessException {
            Field oldField = classToChange.getDeclaredField(oldFieldName);
            oldField.setAccessible(true);
            String oldValue = (String) oldField.get(reflectedObject);
            if (StringUtils.isNotBlank(oldValue)) {
                Field newField = classToChange.getDeclaredField(newFieldName);
                RepositoryConf newValue = new RepositoryConf(oldValue, oldValue, false);
                newField.setAccessible(true);
                newField.set(reflectedObject, newValue);
            }
        }

        @Override
        protected void callback(ServerDetails server, UnmarshallingContext context) {
            try {
                convertToReleaseAndSnapshotRepository(server);
                convertToDynamicReposSelection(server);
            } catch (NoSuchFieldException e) {
                throw new RuntimeException(getConversionErrorMessage(server), e);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(getConversionErrorMessage(server), e);
            }
        }

        private String getConversionErrorMessage(ServerDetails serverDetails) {
            return String.format("Could not convert the class '%s' to use the new overriding Resolve repositories."
                    , serverDetails.getClass().getName());
        }
    }
}