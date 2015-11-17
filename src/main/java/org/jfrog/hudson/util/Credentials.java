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

import hudson.util.Scrambler;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;

/**
 * Credentials model object
 *
 * @author Noam Y. Tenne
 */
public class Credentials implements Serializable {
    public static final Credentials EMPTY_CREDENTIALS = new Credentials(StringUtils.EMPTY, StringUtils.EMPTY);
    private final String username;
    private final String password;

    /**
     * Main constructor
     *
     * @param username Username
     * @param password Clear-text password. Will be scrambled.
     */
    public Credentials(String username, String password) {
        this.username = username;
        this.password = Scrambler.scramble(password);
    }

    /**
     * Constructor bounded to global.jelly
     * It
     */
    @DataBoundConstructor
    public Credentials(String username, String password, String resolverUsername, String resolverPassword) {
        if (StringUtils.isNotBlank(resolverUsername) && StringUtils.isNotBlank(resolverPassword)) {
            username = resolverUsername;
            password = resolverPassword;
        }

        this.username = username;
        this.password = Scrambler.scramble(password);
    }

    /**
     * Returns the username
     *
     * @return Username
     */
    public String getUsername() {
        return username;
    }

    /**
     * Returns the clear-text password (unscrambled)
     *
     * @return Password
     */
    public String getPassword() {
        return Scrambler.descramble(password);
    }
}
