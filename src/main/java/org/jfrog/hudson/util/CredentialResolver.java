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
import org.jfrog.hudson.DeployerOverrider;
import org.jfrog.hudson.ResolverOverrider;

/**
 * A utility class the helps find the preferred credentials to use out of each setting and server
 *
 * @author Noam Y. Tenne
 */
public abstract class CredentialResolver {

    private CredentialResolver() {
    }

    /**
     * Decides and returns the preferred deployment credentials to use from this builder settings and selected server
     *
     * @param deployerOverrider Deploy-overriding capable builder
     * @param server            Selected Artifactory server
     * @return Preferred deployment credentials
     */
    public static Credentials getPreferredDeployer(DeployerOverrider deployerOverrider, ArtifactoryServer server) {
        if (deployerOverrider.isOverridingDefaultDeployer()) {
            return deployerOverrider.getOverridingDeployerCredentials();
        }

        if (server != null) {
            Credentials deployerCredentials = server.getDeployerCredentials();
            if (deployerCredentials != null) {
                return deployerCredentials;
            }
        }

        return new Credentials(null, null);
    }

    /**
     * Decides and returns the preferred resolver credentials to use from this builder settings and selected server
     *
     * @param resolverOverrider Resolve-overriding capable builder
     * @param deployerOverrider Deploy-overriding capable builder
     * @param server            Selected Artifactory server
     * @return Preferred resolver credentials
     */
    public static Credentials getPreferredResolver(ResolverOverrider resolverOverrider,
                                                   DeployerOverrider deployerOverrider, ArtifactoryServer server) {
        if (resolverOverrider != null && resolverOverrider.isOverridingDefaultResolver()) {
            return resolverOverrider.getOverridingResolverCredentials();
        }

        if (deployerOverrider != null && deployerOverrider.isOverridingDefaultDeployer()) {
            return deployerOverrider.getOverridingDeployerCredentials();
        }

        return server.getResolvingCredentials();
    }
}
