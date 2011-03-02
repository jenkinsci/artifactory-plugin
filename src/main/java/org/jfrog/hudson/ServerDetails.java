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

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Artifacts resolution and deployment configuration.
 */
public class ServerDetails {
    /**
     * Artifactory server URL
     */
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
     * Key of repository to use to download artifacts
     */
    public final String downloadRepositoryKey;

    @DataBoundConstructor
    public ServerDetails(String artifactoryName, String repositoryKey, String snapshotsRepositoryKey,
            String downloadRepositoryKey) {
        this.artifactoryName = artifactoryName;
        this.repositoryKey = repositoryKey;
        this.snapshotsRepositoryKey = snapshotsRepositoryKey != null ? snapshotsRepositoryKey : repositoryKey;
        this.downloadRepositoryKey = downloadRepositoryKey;
    }
}
