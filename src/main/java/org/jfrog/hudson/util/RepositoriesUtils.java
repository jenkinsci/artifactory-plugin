package org.jfrog.hudson.util;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Hudson;
import hudson.model.Result;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jfrog.build.api.util.NullLog;
import org.jfrog.build.client.ProxyConfiguration;
import org.jfrog.build.extractor.clientConfiguration.client.ArtifactoryBuildInfoClient;
import org.jfrog.hudson.*;

import java.io.IOException;
import java.util.List;

/**
 * @author Shay Yaakov
 */
public abstract class RepositoriesUtils {

    public static List<String> getReleaseRepositoryKeysFirst(DeployerOverrider deployer, ArtifactoryServer server) {
        if (server == null) {
            return Lists.newArrayList();
        }

        return server.getReleaseRepositoryKeysFirst(deployer);
    }

    public static List<String> getSnapshotRepositoryKeysFirst(DeployerOverrider deployer, ArtifactoryServer server) {
        if (server == null) {
            return Lists.newArrayList();
        }

        return server.getSnapshotRepositoryKeysFirst(deployer);
    }

    public static List<VirtualRepository> getVirtualRepositoryKeys(ResolverOverrider resolverOverrider,
                                                                   DeployerOverrider deployerOverrider,
                                                                   ArtifactoryServer server) {
        if (server == null) {
            return Lists.newArrayList();
        }

        return server.getVirtualRepositoryKeys(resolverOverrider);
    }

    public static List<VirtualRepository> generateVirtualRepos(ArtifactoryBuildInfoClient client) throws IOException {
        List<VirtualRepository> virtualRepositories;

        List<String> keys = client.getVirtualRepositoryKeys();
        virtualRepositories = Lists.newArrayList(Lists.transform(keys, new Function<String, VirtualRepository>() {
            public VirtualRepository apply(String from) {
                return new VirtualRepository(from, from);
            }
        }));

        return virtualRepositories;
    }

    public static List<VirtualRepository> getVirtualRepositoryKeys(String url, CredentialsConfig credentialsConfig,
                                                                   ArtifactoryServer artifactoryServer)
            throws IOException {
        List<VirtualRepository> virtualRepositories;
        CredentialsConfig preferredResolver = CredentialManager.getPreferredResolver(credentialsConfig, artifactoryServer);

        ArtifactoryBuildInfoClient client;
        if (StringUtils.isNotBlank(preferredResolver.provideUsername())) {
            client = new ArtifactoryBuildInfoClient(url, preferredResolver.provideUsername(),
                    preferredResolver.providePassword(), new NullLog());
        } else {
            client = new ArtifactoryBuildInfoClient(url, new NullLog());
        }
        client.setConnectionTimeout(artifactoryServer.getTimeout());

        if (Jenkins.getInstance().proxy != null && !artifactoryServer.isBypassProxy()) {
            client.setProxyConfiguration(createProxyConfiguration(Jenkins.getInstance().proxy));
        }

        virtualRepositories = RepositoriesUtils.generateVirtualRepos(client);
        return virtualRepositories;
    }

    public static List<String> getLocalRepositories(String url, CredentialsConfig credentialsConfig,
                                                    ArtifactoryServer artifactoryServer) throws IOException {
        List<String> localRepository;
        CredentialsConfig preferredDeployer = CredentialManager.getPreferredDeployer(credentialsConfig, artifactoryServer);

        ArtifactoryBuildInfoClient client;
        if (StringUtils.isNotBlank(preferredDeployer.provideUsername())) {
            client = new ArtifactoryBuildInfoClient(url, preferredDeployer.provideUsername(),
                    preferredDeployer.providePassword(), new NullLog());
        } else {
            client = new ArtifactoryBuildInfoClient(url, new NullLog());
        }
        client.setConnectionTimeout(artifactoryServer.getTimeout());

        if (Jenkins.getInstance().proxy != null && !artifactoryServer.isBypassProxy()) {
            client.setProxyConfiguration(createProxyConfiguration(Jenkins.getInstance().proxy));
        }

        localRepository = client.getLocalRepositoriesKeys();

        return localRepository;
    }

    public static ProxyConfiguration createProxyConfiguration(hudson.ProxyConfiguration proxy) {
        ProxyConfiguration proxyConfiguration = null;
        if (proxy != null) {
            proxyConfiguration = new ProxyConfiguration();
            proxyConfiguration.host = proxy.name;
            proxyConfiguration.port = proxy.port;
            proxyConfiguration.username = proxy.getUserName();
            proxyConfiguration.password = proxy.getPassword();
        }

        return proxyConfiguration;
    }

    public static ArtifactoryServer getArtifactoryServer(String artifactoryIdentity, List<ArtifactoryServer> artifactoryServers) {
        if (artifactoryServers != null) {
            for (ArtifactoryServer server : artifactoryServers) {
                if (server.getUrl().equals(artifactoryIdentity) || server.getName().equals(artifactoryIdentity)) {
                    return server;
                }
            }
        }
        return null;
    }

    /**
     * Returns the list of {@link org.jfrog.hudson.ArtifactoryServer} configured.
     *
     * @return can be empty but never null.
     */
    public static List<ArtifactoryServer> getArtifactoryServers() {
        ArtifactoryBuilder.DescriptorImpl descriptor = (ArtifactoryBuilder.DescriptorImpl)
                Hudson.getInstance().getDescriptor(ArtifactoryBuilder.class);
        return descriptor.getArtifactoryServers();
    }

    public static List<Repository> createRepositoriesList(List<String> repositoriesValueList) {
        List<Repository> repositories = Lists.newArrayList();
        for (String repositoryKey : repositoriesValueList) {
            Repository repository = new Repository(repositoryKey);
            repositories.add(repository);
        }
        return repositories;
    }

    public static List<VirtualRepository> collectVirtualRepositories(List<VirtualRepository> repositories, String repoKey) {
        if (repositories == null) {
            repositories = Lists.newArrayList();
        }
        if (StringUtils.isNotBlank(repoKey)) {
            for (VirtualRepository vr : repositories) {
                if (repoKey.equals(vr.getDisplayName())) {
                    return repositories;
                }
            }
            VirtualRepository vr = new VirtualRepository(repoKey, repoKey);
            repositories.add(vr);
        }
        return repositories;
    }

    public static List<Repository> collectRepositories(List<Repository> repositories, String repoKey) {
        if (repositories == null) {
            if (StringUtils.isNotBlank(repoKey)) {
                Repository r = new Repository(repoKey);
                repositories = Lists.newArrayList(r);
            }
        }
        return repositories;
    }

    public static void validateServerConfig(AbstractBuild build, BuildListener listener,
            ArtifactoryServer artifactoryServer, String artifactoryUrl) throws IOException {
        if (artifactoryServer == null) {
            String error = "No Artifactory server configured for " + artifactoryUrl +
                    ". Please check your configuration.";
            listener.getLogger().println(error);
            build.setResult(Result.FAILURE);
            throw new IOException(error);
        }
    }
}
