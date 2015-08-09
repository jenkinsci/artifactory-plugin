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
import hudson.util.Scrambler;
import hudson.util.XStream2;
import org.apache.commons.lang.StringUtils;
import org.jfrog.hudson.DeployerOverrider;
import org.jfrog.hudson.util.Credentials;

import java.lang.reflect.Field;
import java.util.List;

/**
 * General converter for BuildWrapper or Publisher
 *
 * @author Noam Y. Tenne
 * @author Lior Hasson
 */
public class DeployerResolverOverriderConverter<T extends DeployerOverrider>
        extends XStream2.PassthruConverter<T> {

    List<String> converterErrors = Lists.newArrayList();

    public DeployerResolverOverriderConverter(XStream2 xstream) {
        super(xstream);
    }

    @Override
    protected void callback(T overrider, UnmarshallingContext context) {
        Class<? extends DeployerOverrider> overriderClass = overrider.getClass();
        overrideDeployerCredentials(overrider, overriderClass);
        overrideResolverDetails(overrider, overriderClass);

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
        }
        catch (NoSuchFieldException e) {
            converterErrors.add(getConversionErrorMessage(deployerOverrider, e));
        } catch (IllegalAccessException e) {
            converterErrors.add(getConversionErrorMessage(deployerOverrider, e));
        }
    }

    /**
     * Convert the (ServerDetails)details to (ServerDetails)resolverDetails if it doesn't exists already
     */
    private void overrideResolverDetails(T resolverOverrider, Class<? extends DeployerOverrider> overriderClass) {

        try{
            Field resolverDetailsField = overriderClass.getDeclaredField("resolverDetails");
            resolverDetailsField.setAccessible(true);
            Object resolverDetails = resolverDetailsField.get(resolverOverrider);

            if (resolverDetails == null) {
                Field deployerDetailsField = overriderClass.getDeclaredField("details");
                deployerDetailsField.setAccessible(true);
                Object deployerDetails = deployerDetailsField.get(resolverOverrider);

                resolverDetailsField.set(resolverOverrider, deployerDetails);
            }
        }
        catch (NoSuchFieldException e) {
            converterErrors.add(getConversionErrorMessage(resolverOverrider, e));
        } catch (IllegalAccessException e) {
            converterErrors.add(getConversionErrorMessage(resolverOverrider, e));
        }
    }

    private String getConversionErrorMessage(T deployerOverrider, Exception e) {
        return String.format("Could not convert the class '%s' to use the new overriding" +
                "format. Cause: %s", deployerOverrider.getClass().getName(), e.getCause());
    }
}
