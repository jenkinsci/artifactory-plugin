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

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.google.common.collect.Lists;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import hudson.util.Scrambler;
import hudson.util.XStream2;
import jenkins.model.Jenkins;
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
        overrideDeployerCredentials(overrider, overriderClass);
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

    /**
     * Convert any remaining local credential variables to a credentials object.
     * When upgrading from an older version, a user might have deployer credentials as local variables of a builder
     * configuration. This converter Will check for existing old deployer credentials and "move" them to a credentials
     * object instead, thus overriding the deployer credentials of the global config.
     */
    private void overrideDeployerCredentials(T deployerOverrider, Class<? extends DeployerOverrider> overriderClass) {
        try {
            Field oldUsernameField = overriderClass.getDeclaredField("username");
            oldUsernameField.setAccessible(true);
            Object oldUsernameValue = oldUsernameField.get(deployerOverrider);

            if (oldUsernameValue != null && StringUtils.isNotBlank((String) oldUsernameValue) &&
                    !deployerOverrider.isOverridingDefaultDeployer()) {

                String oldUsername = (String) oldUsernameValue;
                String oldPassword = null;
                Field oldPasswordField;
                try {
                    oldPasswordField = overriderClass.getDeclaredField("password");
                } catch (NoSuchFieldException e) {
                    oldPasswordField = overriderClass.getDeclaredField("scrambledPassword");
                }
                oldPasswordField.setAccessible(true);
                Object oldPasswordValue = oldPasswordField.get(deployerOverrider);
                if ((oldPasswordValue != null) && StringUtils.isNotBlank(((String) oldPasswordValue))) {
                    oldPassword = Scrambler.descramble((String) oldPasswordValue);
                }

                Field overridingCredentialsField = overriderClass.getDeclaredField("overridingDeployerCredentials");
                overridingCredentialsField.setAccessible(true);
                overridingCredentialsField.set(deployerOverrider, new Credentials(oldUsername, oldPassword));
            }
        } catch (NoSuchFieldException e) {
            converterErrors.add(getConversionErrorMessage(deployerOverrider, e));
        } catch (IllegalAccessException e) {
            converterErrors.add(getConversionErrorMessage(deployerOverrider, e));
        }
    }

    private void deployerMigration(T overrider, Class<? extends DeployerOverrider> overriderClass) throws NoSuchFieldException, IllegalAccessException, IOException {
        Field overridingDeployerCredentialsField = overriderClass.getDeclaredField("overridingDeployerCredentials");
        overridingDeployerCredentialsField.setAccessible(true);
        Object overridingDeployerCredentials = overridingDeployerCredentialsField.get(overrider);

        Field deployerCredentialsConfigField = overriderClass.getDeclaredField("deployerCredentialsConfig");
        deployerCredentialsConfigField.setAccessible(true);

        if (overridingDeployerCredentials != null) {
            CredentialsStore store = CredentialsProvider.lookupStores(Jenkins.getInstance()).iterator().next();
            String userName = ((Credentials) overridingDeployerCredentials).getUsername();
            String password = ((Credentials) overridingDeployerCredentials).getPassword();

            if (StringUtils.isNotBlank(userName)) {
                String credentialId = userName + ":" + password + ":" + overriderClass.getName() + ":deployer";
                UsernamePasswordCredentialsImpl usernamePasswordCredentials = new UsernamePasswordCredentialsImpl(
                        CredentialsScope.GLOBAL, credentialId,
                        "Migrated from Artifactory plugin Job: " + overrider.getClass().getSimpleName() + " (deployer)",
                        userName, password
                );

                if (!store.getCredentials(Domain.global()).contains(usernamePasswordCredentials)) {
                    store.addCredentials(Domain.global(), usernamePasswordCredentials);
                }
                CredentialsConfig credentialsConfig = new CredentialsConfig(new Credentials(userName, password), credentialId);
                deployerCredentialsConfigField.set(overrider, credentialsConfig);
            }
        } else {
            deployerCredentialsConfigField.set(overrider, CredentialsConfig.createEmptyCredentialsConfigObject());
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
                CredentialsStore store = CredentialsProvider.lookupStores(Jenkins.getInstance()).iterator().next();
                String userName = ((Credentials) resolverCredentials).getUsername();
                String password = ((Credentials) resolverCredentials).getPassword();

                if (StringUtils.isNotBlank(userName)) {
                    String credentialId = userName + ":" + password + ":" + overriderClass.getName() + ":resolver";
                    UsernamePasswordCredentialsImpl usernamePasswordCredentials = new UsernamePasswordCredentialsImpl(
                            CredentialsScope.GLOBAL, credentialId,
                            "Migrated from Artifactory plugin Job: " + overrider.getClass().getSimpleName() + " (resolver)",
                            userName, password
                    );

                    if (!store.getCredentials(Domain.global()).contains(usernamePasswordCredentials)) {
                        store.addCredentials(Domain.global(), usernamePasswordCredentials);
                    }

                    CredentialsConfig credentialsConfig = new CredentialsConfig(new Credentials(userName, password), credentialId);
                    resolverCredentialsConfigField.set(overrider, credentialsConfig);
                }
            } else {
                resolverCredentialsConfigField.set(overrider, CredentialsConfig.createEmptyCredentialsConfigObject());
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
