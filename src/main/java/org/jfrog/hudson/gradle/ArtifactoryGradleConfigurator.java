/*
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2010 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.jfrog.hudson.gradle;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.FreeStyleProject;
import hudson.model.Hudson;
import hudson.model.Result;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.Scrambler;
import net.sf.json.JSONObject;
import org.apache.commons.io.FileUtils;
import org.jfrog.hudson.ArtifactoryBuilder;
import org.jfrog.hudson.ArtifactoryRedeployPublisher;
import org.jfrog.hudson.ArtifactoryServer;
import org.jfrog.hudson.BuildInfoResultAction;
import org.jfrog.hudson.ServerDetails;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;


/**
 * Gradle-Artifactory plugin configuration, allows to add the server details, deployment username/password, as well as
 * flags to deploy ivy, maven, and artifacts, as well as specifications of the location of the remote plugin (.gradle)
 * groovy script.
 *
 * @author Tomer Cohen
 */
@XStreamAlias("artifactory-gradle-config")
public class ArtifactoryGradleConfigurator extends BuildWrapper {
    private ServerDetails details;
    private String username;
    private String scrambledPassword;
    private boolean deployArtifacts;
    public final boolean deployMaven;
    public final boolean deployIvy;
    public final String remotePluginLocation;
    public final boolean deployBuildInfo;
    public final boolean includeEnvVars;


    @DataBoundConstructor
    public ArtifactoryGradleConfigurator(ServerDetails details, boolean deployMaven, boolean deployIvy,
            boolean deployArtifacts, String username, String password, String remotePluginLocation,
            boolean includeEnvVars, boolean deployBuildInfo) {
        this.details = details;
        this.deployMaven = deployMaven;
        this.deployIvy = deployIvy;
        this.deployArtifacts = deployArtifacts;
        this.username = username;
        this.remotePluginLocation = remotePluginLocation;
        this.includeEnvVars = includeEnvVars;
        this.deployBuildInfo = deployBuildInfo;
        this.scrambledPassword = Scrambler.scramble(password);
    }

    public ServerDetails getDetails() {
        return details;
    }

    public String getUsername() {
        return username;
    }

    public boolean isDeployBuildInfo() {
        return deployBuildInfo;
    }

    public boolean isIncludeEnvVars() {
        return includeEnvVars;
    }

    public String getRepositoryKey() {
        return details != null ? details.repositoryKey : null;
    }

    public String getDownloadRepositoryKey() {
        return details != null ? details.downloadRepositoryKey : null;
    }

    public String getArtifactoryName() {
        return details != null ? details.artifactoryName : null;
    }

    public String getPassword() {
        return Scrambler.descramble(scrambledPassword);
    }

    public boolean isDeployArtifacts() {
        return deployArtifacts;
    }

    public boolean isDeployMaven() {
        return deployMaven;
    }

    public boolean isDeployIvy() {
        return deployIvy;
    }

    @Override
    public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener)
            throws IOException, InterruptedException {
        ArtifactoryServer artifactoryServer = getArtifactoryServer();
        if (artifactoryServer == null) {
            listener.getLogger().format("No Artifactory server configured for %s. " +
                    "Please check your configuration.", getArtifactoryName()).println();
            build.setResult(Result.FAILURE);
        }
        GradleInitScriptWriter writer = new GradleInitScriptWriter(this, build.getEnvironment(listener), build);
        File initScript = new File(build.getArtifactsDir().getParent(), ("init-artifactory.gradle"));
        String path = initScript.getAbsolutePath();
        path = path.replace('\\', '/');
        initScript = new File(path);
        try {
            FileUtils.writeStringToFile(initScript, writer.generateInitScript(), "UTF-8");
        } catch (Exception e) {
            listener.getLogger().println("Error occurred while writing Gradle Init Script");
            build.setResult(Result.FAILURE);
        }
        String filePath = initScript.getAbsolutePath();
        filePath = filePath.replace('\\', '/');
        final String finalFilePath = "\"" + filePath + "\"";
        return new Environment() {
            @Override
            public void buildEnvVars(Map<String, String> env) {
                env.put("GRADLE_EXT_SWITCHES", "--init-script " + finalFilePath);
                env.put("GRADLE_EXT_TASKS", "buildInfo");
            }

            @Override
            public boolean tearDown(AbstractBuild build, BuildListener listener)
                    throws IOException, InterruptedException {
                Result result = build.getResult();
                if (result == null) {
                    return false;
                }
                if (result.isBetterOrEqualTo(Result.SUCCESS)) {
                    ArtifactoryRedeployPublisher publisher =
                            new ArtifactoryRedeployPublisher(getDetails(), deployArtifacts, username, getPassword(),
                                    includeEnvVars);
                    if (isDeployBuildInfo()) {
                        build.getActions().add(new BuildInfoResultAction(publisher, build));
                    }
                    return true;
                }
                return false;
            }
        };
    }


    public ArtifactoryServer getArtifactoryServer() {
        List<ArtifactoryServer> servers = getDescriptor().getArtifactoryServers();
        for (ArtifactoryServer server : servers) {
            if (server.getName().equals(getArtifactoryName())) {
                return server;
            }
        }
        return null;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static class DescriptorImpl extends BuildWrapperDescriptor {
        public DescriptorImpl() {
            super(ArtifactoryGradleConfigurator.class);
            load();
        }

        @Override
        public boolean isApplicable(AbstractProject<?, ?> item) {
            return item.getClass().isAssignableFrom(FreeStyleProject.class);
        }

        @Override
        public String getDisplayName() {
            return "Use Gradle-Artifactory Plugin";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            req.bindParameters(this, "gradle");
            save();
            return true;
        }

        /**
         * Returns the list of {@link org.jfrog.hudson.ArtifactoryServer} configured.
         *
         * @return can be empty but never null.
         */
        public List<ArtifactoryServer> getArtifactoryServers() {
            ArtifactoryBuilder.DescriptorImpl descriptor = (ArtifactoryBuilder.DescriptorImpl)
                    Hudson.getInstance().getDescriptor(ArtifactoryBuilder.class);
            return descriptor.getArtifactoryServers();
        }
    }
}
