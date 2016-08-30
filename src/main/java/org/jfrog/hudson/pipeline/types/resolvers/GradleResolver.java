package org.jfrog.hudson.pipeline.types.resolvers;

import hudson.model.Run;
import org.apache.commons.lang.StringUtils;
import org.jfrog.hudson.CredentialsConfig;
import org.jfrog.hudson.RepositoryConf;
import org.jfrog.hudson.ServerDetails;
import org.jfrog.hudson.util.CredentialManager;
import org.jfrog.hudson.util.ResolverContext;

/**
 * Created by Tamirh on 16/08/2016.
 */
public class GradleResolver extends Resolver {

    public ServerDetails getResolverDetails() {
        RepositoryConf snapshotRepositoryConf = null;
        RepositoryConf releaesRepositoryConf = new RepositoryConf(releaseRepo, releaseRepo, false);
        return new ServerDetails(this.server.getServerName(), this.server.getUrl(), releaesRepositoryConf, snapshotRepositoryConf, releaesRepositoryConf, snapshotRepositoryConf, "", "");
    }

    public ResolverContext getResolverContext(Run build) {
        ResolverContext resolverContext = null;
        if (StringUtils.isNotBlank(this.getReleaseRepo())) {
            CredentialsConfig resolverCredentials = CredentialManager.getPreferredResolver(
                    this, getArtifactoryServer());
            resolverContext = new ResolverContext(getArtifactoryServer(), getResolverDetails(),
                    resolverCredentials.getCredentials(build.getParent()), this);
        }
        return resolverContext;
    }
}

