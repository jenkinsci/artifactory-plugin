package org.jfrog.hudson;

import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import hudson.EnvVars;
import hudson.maven.MavenBuild;
import hudson.maven.MavenModule;
import hudson.maven.MavenModuleSetBuild;
import hudson.maven.reporters.MavenArtifact;
import hudson.maven.reporters.MavenArtifactRecord;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Hudson;
import hudson.model.Result;
import hudson.tasks.Fingerprinter;
import org.apache.commons.lang.StringUtils;
import org.jfrog.build.api.Agent;
import org.jfrog.build.api.Artifact;
import org.jfrog.build.api.Build;
import org.jfrog.build.api.BuildAgent;
import org.jfrog.build.api.BuildInfoProperties;
import org.jfrog.build.api.BuildType;
import org.jfrog.build.api.builder.ArtifactBuilder;
import org.jfrog.build.api.builder.BuildInfoBuilder;
import org.jfrog.build.api.builder.DependencyBuilder;
import org.jfrog.build.api.builder.ModuleBuilder;
import org.jfrog.build.client.ArtifactoryBuildInfoClient;
import org.jfrog.hudson.util.ActionableHelper;

import java.io.IOException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Map;
import java.util.Set;

/**
 * Builds and deploys the build info.
 *
 * @author Yossi Shaul
 * @deprecated All maven specific classes moved to org.jfrog.hudson.maven.
 */
@Deprecated
public class BuildInfoDeployer {
    private final ArtifactoryRedeployPublisher publisher;
    private final ArtifactoryBuildInfoClient client;
    private final MavenModuleSetBuild build;
    private final BuildListener listener;

    public BuildInfoDeployer(ArtifactoryRedeployPublisher publisher, ArtifactoryBuildInfoClient client,
            MavenModuleSetBuild build, BuildListener listener) {
        this.publisher = publisher;
        this.client = client;
        this.build = build;
        this.listener = listener;
    }

    public void deploy() throws IOException, InterruptedException {
        Build buildInfo = gatherBuildInfo(build);
        listener.getLogger().println("Deploying build info ...");
        client.sendBuildInfo(buildInfo);
    }

    private Build gatherBuildInfo(MavenModuleSetBuild build) throws IOException, InterruptedException {
        BuildInfoBuilder infoBuilder = new BuildInfoBuilder(build.getParent().getDisplayName())
                .number(build.getNumber() + "")
                .buildAgent(new BuildAgent("Maven", build.getParent().getMaven().getName()))
                .agent(new Agent("hudson", build.getHudsonVersion())).type(BuildType.MAVEN);

        if (Hudson.getInstance().getRootUrl() != null) {
            infoBuilder.url(Hudson.getInstance().getRootUrl() + build.getUrl());
        }

        Calendar startedTimestamp = build.getTimestamp();
        infoBuilder.startedDate(startedTimestamp.getTime());

        long duration = System.currentTimeMillis() - startedTimestamp.getTimeInMillis();
        infoBuilder.durationMillis(duration);

        ArtifactoryServer server = publisher.getArtifactoryServer();
        infoBuilder.artifactoryPrincipal(server.getUserName());

        CauseAction action = ActionableHelper.getLatestAction(build, CauseAction.class);
        if (action != null) {
            for (Cause cause : action.getCauses()) {
                if (cause instanceof Cause.UserCause) {
                    infoBuilder.principal(((Cause.UserCause) cause).getUserName());
                }
            }
        }

        Cause.UpstreamCause parent = ActionableHelper.getUpstreamCause(build);
        if (parent != null) {
            String parentProject = parent.getUpstreamProject();
            int buildNumber = parent.getUpstreamBuild();
            infoBuilder.parentName(parentProject);
            infoBuilder.parentNumber(buildNumber + "");
        }

        gatherModuleAndDependencyInfo(infoBuilder, build);
        gatherSysPropInfo(infoBuilder);
        addBuildInfoVariables(infoBuilder);
        EnvVars envVars = build.getEnvironment(listener);
        String revision = envVars.get("SVN_REVISION");
        if (StringUtils.isNotBlank(revision)) {
            infoBuilder.vcsRevision(revision);
        }
        if (publisher.isIncludeEnvVars()) {
            for (Map.Entry<String, String> entry : envVars.entrySet()) {
                infoBuilder
                        .addProperty(BuildInfoProperties.BUILD_INFO_ENVIRONMENT_PREFIX + entry.getKey(),
                                entry.getValue());
            }
        } else {
            MapDifference<String, String> difference = Maps.difference(envVars, System.getenv());
            Map<String, String> filteredEnvVars = difference.entriesOnlyOnLeft();
            for (Map.Entry<String, String> entry : filteredEnvVars.entrySet()) {
                infoBuilder.addProperty(BuildInfoProperties.BUILD_INFO_ENVIRONMENT_PREFIX + entry.getKey(),
                        entry.getValue());
            }
        }
        Build buildInfo = infoBuilder.build();
        // for backwards compatibility for Artifactory 2.2.3
        if (parent != null) {
            buildInfo.setParentBuildId(parent.getUpstreamProject());
        }
        return buildInfo;
    }

    private void gatherSysPropInfo(BuildInfoBuilder infoBuilder) {
        infoBuilder.addProperty("os.arch", System.getProperty("os.arch"));
        infoBuilder.addProperty("os.name", System.getProperty("os.name"));
        infoBuilder.addProperty("os.version", System.getProperty("os.version"));
        infoBuilder.addProperty("java.version", System.getProperty("java.version"));
        infoBuilder.addProperty("java.vm.info", System.getProperty("java.vm.info"));
        infoBuilder.addProperty("java.vm.name", System.getProperty("java.vm.name"));
        infoBuilder.addProperty("java.vm.specification.name", System.getProperty("java.vm.specification.name"));
        infoBuilder.addProperty("java.vm.vendor", System.getProperty("java.vm.vendor"));
    }

    private void addBuildInfoVariables(BuildInfoBuilder infoBuilder) {
        Map<String, String> buildVariables = build.getBuildVariables();
        for (Map.Entry<String, String> entry : buildVariables.entrySet()) {
            infoBuilder
                    .addProperty(BuildInfoProperties.BUILD_INFO_ENVIRONMENT_PREFIX + entry.getKey(), entry.getValue());
        }
    }

    private void gatherModuleAndDependencyInfo(BuildInfoBuilder infoBuilder, MavenModuleSetBuild mavenModulesBuild) {
        Map<MavenModule, MavenBuild> mavenBuildMap = mavenModulesBuild.getModuleLastBuilds();
        for (Map.Entry<MavenModule, MavenBuild> moduleBuild : mavenBuildMap.entrySet()) {
            MavenModule mavenModule = moduleBuild.getKey();
            MavenBuild mavenBuild = moduleBuild.getValue();
            Result result = mavenBuild.getResult();
            if (Result.NOT_BUILT.equals(result)) {
                // HAP-52 - the module build might be skipped if using incremental build
                continue;
            }
            MavenArtifactRecord mar = ActionableHelper.getLatestMavenArtifactRecord(mavenBuild);
            String moduleId = mavenModule.getName() + ":" + mavenModule.getVersion();
            ModuleBuilder moduleBuilder = new ModuleBuilder().id(moduleId);

            // add artifacts
            moduleBuilder.addArtifact(toArtifact(mar.mainArtifact, mavenBuild));
            if (!mar.isPOM() && mar.pomArtifact != null && mar.pomArtifact != mar.mainArtifact) {
                moduleBuilder.addArtifact(toArtifact(mar.pomArtifact, mavenBuild));
            }
            for (MavenArtifact attachedArtifact : mar.attachedArtifacts) {
                moduleBuilder.addArtifact(toArtifact(attachedArtifact, mavenBuild));
            }

            addDependencies(moduleBuilder, mavenBuild);

            infoBuilder.addModule(moduleBuilder.build());
        }
    }

    private void addDependencies(ModuleBuilder moduleBuilder, MavenBuild mavenBuild) {
        MavenDependenciesRecord dependenciesRecord =
                ActionableHelper.getLatestAction(mavenBuild, MavenDependenciesRecord.class);
        if (dependenciesRecord != null) {
            Set<MavenDependency> dependencies = dependenciesRecord.getDependencies();
            for (MavenDependency dependency : dependencies) {
                DependencyBuilder dependencyBuilder = new DependencyBuilder()
                        .id(dependency.id)
                        .scopes(Arrays.asList(dependency.scope))
                        .type(dependency.type)
                        .md5(getMd5(dependency.groupId, dependency.fileName, mavenBuild));
                moduleBuilder.addDependency(dependencyBuilder.build());
            }
        }
    }

    private Artifact toArtifact(MavenArtifact mavenArtifact, MavenBuild mavenBuild) {
        ArtifactBuilder artifactBuilder = new ArtifactBuilder(mavenArtifact.canonicalName)
                .type(mavenArtifact.type)
                .md5(getMd5(mavenArtifact.groupId, mavenArtifact.fileName, mavenBuild));
        return artifactBuilder.build();
    }

    private String getMd5(String groupId, String fileName, MavenBuild mavenBuild) {
        String md5 = null;
        Fingerprinter.FingerprintAction fingerprint = ActionableHelper.getLatestAction(
                mavenBuild, Fingerprinter.FingerprintAction.class);
        if (fingerprint != null) {
            md5 = fingerprint.getRecords().get(groupId + ":" + fileName);
        }
        return md5;
    }
}
