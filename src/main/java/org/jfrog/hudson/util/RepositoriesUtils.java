package org.jfrog.hudson.util;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import org.jfrog.build.client.ArtifactoryBuildInfoClient;
import org.jfrog.hudson.ArtifactoryServer;
import org.jfrog.hudson.DeployerOverrider;
import org.jfrog.hudson.ResolverOverrider;
import org.jfrog.hudson.VirtualRepository;

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
                                                                   DeployerOverrider deployerOverrider, ArtifactoryServer server) {
        if (server == null) {
            return Lists.newArrayList();
        }

        return server.getVirtualRepositoryKeys(resolverOverrider, deployerOverrider);
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
}
