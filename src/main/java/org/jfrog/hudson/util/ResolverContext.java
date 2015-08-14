/*
 * Copyright (C) 2011 JFrog Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jfrog.hudson.util;

import org.jfrog.hudson.ArtifactoryServer;
import org.jfrog.hudson.ResolverOverrider;
import org.jfrog.hudson.ServerDetails;

/**
 * Context for resolution. Used by a shared code to set the resolver configuration.
 *
 * @author Yossi Shaul
 */
public class ResolverContext {

    private ServerDetails serverDetails;
    private Credentials credentials;
    private ArtifactoryServer server;
    private ResolverOverrider resolverOverrider;

    public ResolverContext(ArtifactoryServer server, ServerDetails serverDetails, Credentials credentials,
                           ResolverOverrider resolverOverrider) {
        this.serverDetails = serverDetails;
        this.credentials = credentials;
        this.server = server;
        this.resolverOverrider = resolverOverrider;
    }

    public ArtifactoryServer getServer() {
        return server;
    }

    public ServerDetails getServerDetails() {
        return serverDetails;
    }

    public Credentials getCredentials() {
        return credentials;
    }

    public ResolverOverrider getResolverOverrider() {
        return resolverOverrider;
    }

    public void setResolverOverrider(ResolverOverrider resolverOverrider) {
        this.resolverOverrider = resolverOverrider;
    }
}
