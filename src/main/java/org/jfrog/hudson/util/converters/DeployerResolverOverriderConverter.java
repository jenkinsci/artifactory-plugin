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

package org.jfrog.hudson.util.converters;

import com.google.common.collect.Lists;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import hudson.util.XStream2;
import org.apache.commons.lang.StringUtils;
import org.jfrog.hudson.CredentialsConfig;
import org.jfrog.hudson.DeployerOverrider;
import org.jfrog.hudson.ResolverOverrider;
import org.jfrog.hudson.util.Credentials;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.logging.Logger;

/**
 * General converter for BuildWrapper or Publisher
 *
 * @author Noam Y. Tenne
 * @author Lior Hasson
 */
public class DeployerResolverOverriderConverter<T extends DeployerOverrider>
        extends XStream2.PassthruConverter<T> {
    Logger logger = Logger.getLogger(DeployerResolverOverriderConverter.class.getName());
    List<String> converterErrors = Lists.newArrayList();

    public DeployerResolverOverriderConverter(XStream2 xstream) {
        super(xstream);
    }

    @Override
    protected void callback(T overrider, UnmarshallingContext context) {
        Class<? extends DeployerOverrider> overriderClass = overrider.getClass();
        overrideResolverDetails(overrider, overriderClass);
        credentialsMigration(overrider, overriderClass);

        if (!converterErrors.isEmpty()) {
            logger.info(converterErrors.toString());
        }
    }

    /**
     * Migrate to Jenkins "Credentials" plugin from the old credential implementation
     */
    public void credentialsMigration(T overrider, Class<? extends DeployerOverrider> overriderClass) {
        try {
            deployerMigration(overrider, overriderClass);
            resolverMigration(overrider, overriderClass);
        } catch (NoSuchFieldException e) {
            converterErrors.add(getConversionErrorMessage(overrider, e));
        } catch (IllegalAccessException e) {
            converterErrors.add(getConversionErrorMessage(overrider, e));
        } catch (IOException e) {
            converterErrors.add(getConversionErrorMessage(overrider, e));
        }
    }

    private void deployerMigration(T overrider, Class<? extends DeployerOverrider> overriderClass) throws NoSuchFieldException, IllegalAccessException, IOException {

        Field overridingDeployerCredentialsField = overriderClass.getDeclaredField("overridingDeployerCredentials");
        overridingDeployerCredentialsField.setAccessible(true);
        Object overridingDeployerCredentials = overridingDeployerCredentialsField.get(overrider);

        Field deployerCredentialsConfigField = overriderClass.getDeclaredField("deployerCredentialsConfig");
        deployerCredentialsConfigField.setAccessible(true);

        if (overridingDeployerCredentials != null) {
            deployerCredentialsConfigField.set(overrider, new CredentialsConfig((Credentials) overridingDeployerCredentials,
                    StringUtils.EMPTY, true));
        } else {
            if (deployerCredentialsConfigField.get(overrider) == null) {
                deployerCredentialsConfigField.set(overrider, CredentialsConfig.createEmptyCredentialsConfigObject());
            }
        }
    }

    private void resolverMigration(T overrider, Class<? extends DeployerOverrider> overriderClass)
            throws NoSuchFieldException, IllegalAccessException, IOException {

        if (overrider instanceof ResolverOverrider) {
            Field resolverCredentialsField = overriderClass.getDeclaredField("overridingResolverCredentials");
            resolverCredentialsField.setAccessible(true);
            Object resolverCredentials = resolverCredentialsField.get(overrider);

            Field resolverCredentialsConfigField = overriderClass.getDeclaredField("resolverCredentialsConfig");
            resolverCredentialsConfigField.setAccessible(true);
            if (resolverCredentials != null) {
                CredentialsConfig credentialsConfig = new CredentialsConfig((Credentials) resolverCredentials, StringUtils.EMPTY, true);
                resolverCredentialsConfigField.set(overrider, credentialsConfig);
            } else {
                if (resolverCredentialsConfigField.get(overrider) == null) {
                    resolverCredentialsConfigField.set(overrider, CredentialsConfig.createEmptyCredentialsConfigObject());
                }
            }
        }
    }

    /**
     * Convert the (ServerDetails)details to (ServerDetails)resolverDetails if it doesn't exists already
     */
    private void overrideResolverDetails(T overrider, Class<? extends DeployerOverrider> overriderClass) {
        if (overrider instanceof ResolverOverrider) {
            try {
                Field resolverDetailsField = overriderClass.getDeclaredField("resolverDetails");
                resolverDetailsField.setAccessible(true);
                Object resolverDetails = resolverDetailsField.get(overrider);

                if (resolverDetails == null) {
                    Field deployerDetailsField = overriderClass.getDeclaredField("details");
                    deployerDetailsField.setAccessible(true);
                    Object deployerDetails = deployerDetailsField.get(overrider);

                    resolverDetailsField.set(overrider, deployerDetails);
                }
            } catch (NoSuchFieldException e) {
                converterErrors.add(getConversionErrorMessage(overrider, e));
            } catch (IllegalAccessException e) {
                converterErrors.add(getConversionErrorMessage(overrider, e));
            }
        }
    }

    private String getConversionErrorMessage(T deployerOverrider, Exception e) {
        return String.format("Could not convert the class '%s' to use the new overriding" +
                "format. Cause: %s", deployerOverrider.getClass().getName(), e.getCause());
    }

}
