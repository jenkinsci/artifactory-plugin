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

import org.jfrog.hudson.util.Credentials;

/**
 * Represents a class that in the past contained local credential variables which were changed for the option of
 * overriding the default deployer credentials in the global config.<br/> To be used mostly for builder configurations.
 *
 * @author Noam Y. Tenne
 */
public interface DeployerOverrider {

    /**
     * Indicates whether the represented builder is configured to override the default deployment credentials configured
     * in the global config
     *
     * @return True if the builder is overriding the global configuration
     */
    boolean isOverridingDefaultDeployer();

    /**
     * Returns the global config - overriding deployer credentials
     *
     * @return Deployer credentials
     *
     * @deprecated: Use org.jfrog.hudson.DeployerOverrider#getDeployerCredentialsConfig()
     */
    @Deprecated
    Credentials getOverridingDeployerCredentials();

    CredentialsConfig getDeployerCredentialsConfig();

}