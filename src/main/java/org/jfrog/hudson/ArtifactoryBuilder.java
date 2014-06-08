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

import hudson.Extension;
import hudson.Launcher;
import hudson.maven.MavenModuleSet;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jfrog.build.api.util.NullLog;
import org.jfrog.build.client.ArtifactoryBuildInfoClient;
import org.jfrog.build.client.ArtifactoryVersion;
import org.jfrog.hudson.util.RepositoriesUtils;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.List;

/**
 * @author Yossi Shaul
 */
public class ArtifactoryBuilder extends Builder {

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
        // this is where you 'build' the project
        List<ArtifactoryServer> servers = getDescriptor().getArtifactoryServers();
        if (servers.isEmpty()) {
            listener.getLogger().println("No Artifactory server configured");
        } else {
            listener.getLogger().println(servers.size() + " Artifactory servers configured");
        }
        return true;
    }

    // override for better type safety.
    // if your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    /**
     * Descriptor for {@link ArtifactoryBuilder}. Used as a singleton. The class is marked as public so that it can be
     * accessed from views.
     * <p/>
     * <p/>
     * See <tt>views/hudson/plugins/artifactory/ArtifactoryBuilder/*.jelly</tt> for the actual HTML fragment for the
     * configuration screen.
     */
    @Extension
    // this marker indicates Hudson that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        private List<ArtifactoryServer> artifactoryServers;

        public DescriptorImpl() {
            super(ArtifactoryBuilder.class);
            load();
        }

        /**
         * Performs on-the-fly validation of the form field 'name'.
         *
         * @param value This parameter receives the value that the user has typed.
         * @return Indicates the outcome of the validation. This is sent to the browser.
         */
        public FormValidation doCheckName(@QueryParameter String value) throws IOException, ServletException {
            if (value.length() == 0) {
                return FormValidation.error("Please set a name");
            }
            if (value.length() < 4) {
                return FormValidation.warning("Isn't the name too short?");
            }
            return FormValidation.ok();
        }

        public FormValidation doTestConnection(@QueryParameter("artifactoryUrl") final String url,
                                               @QueryParameter("username") final String deployerUsername,
                                               @QueryParameter("password") final String deployerPassword,
                                               @QueryParameter("resolverCredentials") final boolean resolverCredentials,
                                               @QueryParameter("resolverUsername") final String resolverUsername,
                                               @QueryParameter("resolverPassword") final String resolverPassword,
                                               @QueryParameter("artifactory.timeout") final String timeout) throws ServletException {

            String username = deployerUsername;
            String password = deployerPassword;
            if (resolverCredentials && StringUtils.isNotBlank(resolverUsername) && StringUtils.isNotBlank(resolverPassword)) {
                username = resolverUsername;
                password = resolverPassword;
            }

            if (StringUtils.isBlank(url)) {
                return FormValidation.error("Please set a valid Artifactory URL");
            }

            ArtifactoryBuildInfoClient client;
            if (StringUtils.isNotBlank(username)) {
                client = new ArtifactoryBuildInfoClient(url, username, password, new NullLog());
            } else {
                client = new ArtifactoryBuildInfoClient(url, new NullLog());
            }

            ArtifactoryServer artifactoryServer = RepositoriesUtils.getArtifactoryServer(url, getArtifactoryServers());

            if (!artifactoryServer.isBypassProxy() && Jenkins.getInstance().proxy != null) {
                client.setProxyConfiguration(RepositoriesUtils.createProxyConfiguration(Jenkins.getInstance().proxy));
            }

            if (StringUtils.isNotBlank(timeout))
                client.setConnectionTimeout(Integer.parseInt(timeout));
            else
                client.setConnectionTimeout(artifactoryServer.getTimeout());

            ArtifactoryVersion version;
            try {
                version = client.verifyCompatibleArtifactoryVersion();
            } catch (UnsupportedOperationException uoe) {
                return FormValidation.warning(uoe.getMessage());
            } catch (Exception e) {
                return FormValidation.error(e.getMessage());
            }
            return FormValidation.ok("Found Artifactory " + version.toString());
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // indicates that this builder can be used with all kinds of project types
            return aClass == MavenModuleSet.class;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        @Override
        public String getDisplayName() {
            return "Artifactory Plugin";
        }

        @SuppressWarnings({"unchecked"})
        @Override
        public boolean configure(StaplerRequest req, JSONObject o) throws FormException {
            Object servers = o.get("artifactoryServer");    // an array or single object
            if (!JSONNull.getInstance().equals(servers)) {
                artifactoryServers = req.bindJSONToList(ArtifactoryServer.class, servers);
            } else {
                artifactoryServers = null;
            }
            save();
            return super.configure(req, o);
        }

        public List<ArtifactoryServer> getArtifactoryServers() {
            return artifactoryServers;
        }

        // Required by external plugins.
        @SuppressWarnings({"UnusedDeclaration"})
        public void setArtifactoryServers(List<ArtifactoryServer> artifactoryServers) {
            this.artifactoryServers = artifactoryServers;
        }
    }
}
