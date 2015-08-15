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
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jfrog.build.api.util.NullLog;
import org.jfrog.build.client.ArtifactoryVersion;
import org.jfrog.build.extractor.clientConfiguration.client.ArtifactoryBuildInfoClient;
import org.jfrog.hudson.util.Credentials;
import org.jfrog.hudson.util.RepositoriesUtils;
import org.jfrog.hudson.util.plugins.PluginsUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.List;

/**
 * @author Yossi Shaul
 */
public class ArtifactoryBuilder extends GlobalConfiguration {

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
    public static final class DescriptorImpl extends Descriptor<GlobalConfiguration> {

        private List<ArtifactoryServer> artifactoryServers;

        public DescriptorImpl() {
            super(ArtifactoryBuilder.class);
            load();
        }

        @SuppressWarnings("unused")
        public ListBoxModel doFillDeployerCredentialsIdItems(@AncestorInPath Item project) {
            return PluginsUtils.fillPluginCredentials(project);
        }

        @SuppressWarnings("unused")
        public ListBoxModel doFillResolverCredentialsIdItems(@AncestorInPath Item project) {
            return PluginsUtils.fillPluginCredentials(project);
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

        public FormValidation doTestConnection(
                @QueryParameter("artifactoryUrl") final String url,
                @QueryParameter("deployerCredentialsId") final String deployerCredentialsId,
                @QueryParameter("artifactory.timeout") final String timeout,
                @QueryParameter("artifactory.bypassProxy") final boolean bypassProxy) throws ServletException {

                Credentials credentials = PluginsUtils.credentialsLookup(deployerCredentialsId);
            String username = credentials.getUsername();
            String password = credentials.getPassword();

            if (StringUtils.isBlank(url)) {
                return FormValidation.error("Please set a valid Artifactory URL");
            }

            ArtifactoryBuildInfoClient client;
            if (StringUtils.isNotBlank(username)) {
                client = new ArtifactoryBuildInfoClient(url, username, password, new NullLog());
            } else {
                client = new ArtifactoryBuildInfoClient(url, new NullLog());
            }

            if (!bypassProxy && Jenkins.getInstance().proxy != null) {
                client.setProxyConfiguration(RepositoriesUtils.createProxyConfiguration(Jenkins.getInstance().proxy));
            }

            if (StringUtils.isNotBlank(timeout))
                client.setConnectionTimeout(Integer.parseInt(timeout));

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
