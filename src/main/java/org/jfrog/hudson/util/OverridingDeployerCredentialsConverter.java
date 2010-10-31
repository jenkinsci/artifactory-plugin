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

import com.thoughtworks.xstream.converters.UnmarshallingContext;
import hudson.util.Scrambler;
import hudson.util.XStream2;
import org.apache.commons.lang.StringUtils;
import org.jfrog.hudson.DeployerOverrider;

import java.lang.reflect.Field;

/**
 * When upgrading from an older version, a user might have deployer credentials as local variables of a builder
 * configuration. This converter Will check for existing old deployer credentials and "move" them to a credentials
 * object instead, thus overriding the deployer credentials of the global config.
 *
 * @author Noam Y. Tenne
 */
public class OverridingDeployerCredentialsConverter<T extends DeployerOverrider>
        extends XStream2.PassthruConverter<T> {

    public OverridingDeployerCredentialsConverter(XStream2 xstream) {
        super(xstream);
    }

    @Override
    protected void callback(T deployerOverrider, UnmarshallingContext context) {
        Class<? extends DeployerOverrider> overriderClass = deployerOverrider.getClass();

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
            throw new RuntimeException(getConvertionErrorMessage(deployerOverrider), e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(getConvertionErrorMessage(deployerOverrider), e);
        }
    }

    private String getConvertionErrorMessage(T deployerOverrider) {
        return String.format("Could not convert the class '%s' to use the new overriding deployer credential storage " +
                "format.", deployerOverrider.getClass().getName());
    }
}
