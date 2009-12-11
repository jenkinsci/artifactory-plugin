package org.jfrog.hudson;

import hudson.maven.MavenBuild;
import hudson.maven.MavenModule;
import hudson.maven.MavenModuleSetBuild;
import hudson.maven.reporters.MavenArtifact;
import hudson.maven.reporters.MavenArtifactRecord;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Hudson;
import hudson.tasks.Fingerprinter;
import net.sf.json.JSONObject;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.artifactory.build.api.Agent;
import org.artifactory.build.api.Artifact;
import org.artifactory.build.api.Build;
import org.artifactory.build.api.BuildType;
import org.artifactory.build.api.builder.BuildInfoBuilder;
import org.artifactory.build.api.builder.DependencyBuilder;
import org.artifactory.build.api.builder.ModuleBuilder;

import java.io.IOException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Map;
import java.util.Set;

/**
 * Builds and deployes the build info.
 *
 * @author Yossi Shaul
 */
public class BuildInfoDeployer {
    private final ArtifactoryRedeployPublisher publisher;
    private final MavenModuleSetBuild build;
    private final BuildListener listener;

    public BuildInfoDeployer(ArtifactoryRedeployPublisher publisher, MavenModuleSetBuild build,
            BuildListener listener) {
        this.publisher = publisher;
        this.build = build;
        this.listener = listener;
    }

    public void deploy() throws IOException {
        Build buildInfo = gatherBuildInfo(build);

        sendBuildInfo(buildInfo);
    }

    private Build gatherBuildInfo(MavenModuleSetBuild build) {
        BuildInfoBuilder infoBuilder = new BuildInfoBuilder()
                .name(build.getParent().getDisplayName())
                .number(build.getNumber())
                .type(BuildType.MAVEN)
                .agent(new Agent("hudson", build.getHudsonVersion()));

        if (Hudson.getInstance().getRootUrl() != null) {
            infoBuilder.url(Hudson.getInstance().getRootUrl() + build.getUrl());
        }

        Calendar startedTimestamp = build.getTimestamp();
        infoBuilder.startedDate(startedTimestamp.getTime());

        long duration = System.currentTimeMillis() - startedTimestamp.getTimeInMillis();
        infoBuilder.durationMillis(duration);

        ArtifactoryServer server = publisher.getArtifactoryServer();
        infoBuilder.artifactoryPrincipal(server.getUserName());

        CauseAction action = build.getAction(CauseAction.class);
        if (action != null) {
            for (Cause cause : action.getCauses()) {
                if (cause instanceof Cause.UserCause) {
                    infoBuilder.principal(((Cause.UserCause) cause).getUserName());
                }
            }
        }

        gatherModuleAndDependencyInfo(infoBuilder, build);

        gatherSysPropInfo(infoBuilder);

        return infoBuilder.build();
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

    private void gatherModuleAndDependencyInfo(BuildInfoBuilder infoBuilder, MavenModuleSetBuild mavenModulesBuild) {
        Map<MavenModule, MavenBuild> mavenBuildMap = mavenModulesBuild.getModuleLastBuilds();
        for (Map.Entry<MavenModule, MavenBuild> moduleBuild : mavenBuildMap.entrySet()) {
            MavenModule mavenModule = moduleBuild.getKey();
            MavenBuild mavenBuild = moduleBuild.getValue();
            MavenArtifactRecord mar = mavenBuild.getAction(MavenArtifactRecord.class);
            String moduleId = mavenModule.getName() + ":" + mavenModule.getVersion();
            ModuleBuilder moduleBuilder = new ModuleBuilder().id(moduleId);

            // add artifacts
            moduleBuilder.addArtifact(toArtifact(mar.mainArtifact, mavenBuild));
            if (!mar.isPOM() && mar.pomArtifact != null) {
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
        MavenDependenciesRecord dependenciesRecord = mavenBuild.getAction(MavenDependenciesRecord.class);
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
        Artifact artifact = new Artifact();
        artifact.setName(mavenArtifact.canonicalName);
        artifact.setType(mavenArtifact.type);
        artifact.setMd5(getMd5(mavenArtifact.groupId, mavenArtifact.fileName, mavenBuild));
        return artifact;
    }

    private String getMd5(String groupId, String fileName, MavenBuild mavenBuild) {
        String md5 = null;
        Fingerprinter.FingerprintAction fingerprint = mavenBuild.getAction(Fingerprinter.FingerprintAction.class);
        if (fingerprint != null) {
            md5 = fingerprint.getRecords().get(groupId + ":" + fileName);
        }
        return md5;
    }

    private void sendBuildInfo(Build buildInfo) throws IOException {
        String restUrl = "api/build";
        String url = publisher.getArtifactoryName() + "/" + restUrl;
        JSONObject buildInfoJson = JSONObject.fromObject(buildInfo);
        DefaultHttpClient httpClient =
                publisher.getArtifactoryServer().createHttpClient(
                        publisher.getUsername(), publisher.getPassword());
        HttpPut httpPut = new HttpPut(url);
        StringEntity stringEntity = new StringEntity(buildInfoJson.toString());
        stringEntity.setContentType("application/vnd.org.jfrog.artifactory+json");
        httpPut.setEntity(stringEntity);
        listener.getLogger().println("Deploying build info to: " + url);
        HttpResponse response = httpClient.execute(httpPut);
        if (response.getEntity() != null) {
            response.getEntity().consumeContent();
        }
        if (response.getStatusLine().getStatusCode() != HttpStatus.SC_NO_CONTENT) {
            throw new IOException("Failed to send build info: " + response.getStatusLine().getReasonPhrase());
        }
    }
}
