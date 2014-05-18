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

import com.thoughtworks.xstream.converters.UnmarshallingContext;
import hudson.util.XStream2;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import java.lang.reflect.Field;

/**
 * Artifacts resolution and deployment configuration.
 */
public class ServerDetails {
    public final String artifactoryName;
    /**
     * Key of the repository to deploy release artifacts to
     */
    public final String repositoryKey;
    /**
     * Key of the repository to deploy snapshot artifacts to. If not specified will use the repositoryKey
     */
    public final String snapshotsRepositoryKey;
    /**
     * Key of repository to use to download snapshots artifacts
     */
    public final String downloadSnapshotRepositoryKey;
    /**
     * Key of repository to use to download artifacts
     */
    public final String downloadReleaseRepositoryKey;
    /**
     * Artifactory server URL
     */
    private final String artifactoryUrl;
    private PluginSettings stagingPlugin;
    /**
     * @deprecated: Use org.jfrog.hudson.ServerDetails#downloadReleaseRepositoryKey
     */
    @Deprecated
    private String downloadRepositoryKey;

    @DataBoundConstructor
    public ServerDetails(String artifactoryName, String artifactoryUrl, String repositoryKey, String snapshotsRepositoryKey,
                         String downloadReleaseRepositoryKey, String downloadSnapshotRepositoryKey) {
        this.artifactoryName = artifactoryName;
        this.artifactoryUrl = artifactoryUrl;
        this.repositoryKey = repositoryKey;
        this.snapshotsRepositoryKey = snapshotsRepositoryKey != null ? snapshotsRepositoryKey : repositoryKey;
        this.downloadReleaseRepositoryKey = downloadReleaseRepositoryKey;
        this.downloadSnapshotRepositoryKey = downloadSnapshotRepositoryKey;
    }

    public PluginSettings getStagingPlugin() {
        return stagingPlugin;
    }

    public void setStagingPlugin(PluginSettings stagingPlugin) {
        this.stagingPlugin = stagingPlugin;
    }

    public String getStagingPluginName() {
        return (stagingPlugin != null) ? stagingPlugin.getPluginName() : null;
    }

    public String getPluginParamValue(String pluginName, String paramKey) {
        return (stagingPlugin != null) ? stagingPlugin.getPluginParamValue(pluginName, paramKey) : null;
    }

    public String getArtifactoryUrl() {
        //support legacy code when artifactoryName was the url
        return artifactoryUrl != null ? artifactoryUrl : artifactoryName;
    }

    public static final class ConverterImpl extends XStream2.PassthruConverter<ServerDetails> {
        public ConverterImpl(XStream2 xstream) {
            super(xstream);
        }

        @Override
        protected void callback(ServerDetails server, UnmarshallingContext context) {
            Class<? extends ServerDetails> overrideClass = server.getClass();

            try {
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
