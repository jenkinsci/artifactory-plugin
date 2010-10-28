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
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Credentials model object
 *
 * @author Noam Y. Tenne
 */
public class Credentials {

    private final String username;
    private final String password;

    /**
     * Main constructor
     *
     * @param username Username
     * @param password Clear-text password. Will be scrambled.
     */
    @DataBoundConstructor
    public Credentials(String username, String password) {
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
