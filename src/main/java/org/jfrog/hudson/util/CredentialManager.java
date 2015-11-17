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

package org.jfrog.hudson.util;

import org.jfrog.hudson.ArtifactoryServer;
import org.jfrog.hudson.CredentialsConfig;
import org.jfrog.hudson.DeployerOverrider;
import org.jfrog.hudson.ResolverOverrider;

/**
 * A utility class the helps find the preferred credentials to use out of each setting and server
 *
 * @author Noam Y. Tenne
 */
public abstract class CredentialManager {

    private CredentialManager() {
    }

    /**
     * Decides and returns the preferred deployment credentials to use from this builder settings and selected server
     *
     * @param deployerOverrider Deploy-overriding capable builder
     * @param server            Selected Artifactory server
     * @return Preferred deployment credentials
     */
    public static CredentialsConfig getPreferredDeployer(DeployerOverrider deployerOverrider, ArtifactoryServer server) {
        if (deployerOverrider.isOverridingDefaultDeployer()) {
            CredentialsConfig deployerCredentialsConfig = deployerOverrider.getDeployerCredentialsConfig();
            if (deployerCredentialsConfig != null) {
                return deployerCredentialsConfig;
            }
        }

        if (server != null) {
            CredentialsConfig deployerCredentials = server.getDeployerCredentialsConfig();
            if (deployerCredentials != null) {
                return deployerCredentials;
            }
        }

        return CredentialsConfig.EMPTY_CREDENTIALS_CONFIG;
    }

    public static CredentialsConfig getPreferredDeployer(CredentialsConfig credentialsConfig, ArtifactoryServer server) {
        if (!credentialsConfig.isCredentialsProvided()) {
            return server.getDeployerCredentialsConfig();
        } else {
            return credentialsConfig;
        }
    }

    /**
     * Decides and returns the preferred resolver credentials to use from this builder settings and selected server
     * Override priority:
     * 1) Job override resolver
     * 2) Plugin manage override resolver
     * 3) Plugin manage general
     *
     * @param resolverOverrider Resolve-overriding capable builder
     * @param server            Selected Artifactory server
     * @return Preferred resolver credentials
     */
    public static CredentialsConfig getPreferredResolver(ResolverOverrider resolverOverrider, ArtifactoryServer server) {
        if (resolverOverrider != null && resolverOverrider.isOverridingDefaultResolver()) {
            CredentialsConfig resolverCredentialsConfig = resolverOverrider.getResolverCredentialsConfig();
            if (resolverCredentialsConfig != null) {
                return resolverCredentialsConfig;
            }
        }

        return server.getResolvingCredentialsConfig();
    }


    public static CredentialsConfig getPreferredResolver(CredentialsConfig credentialsConfig, ArtifactoryServer server) {
        if (!credentialsConfig.isCredentialsProvided()) {
            return server.getResolvingCredentialsConfig();
        }
        return credentialsConfig;
    }
}
