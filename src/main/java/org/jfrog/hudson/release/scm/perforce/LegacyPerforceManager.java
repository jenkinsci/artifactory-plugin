package org.jfrog.hudson.release.scm.perforce;

import hudson.model.AbstractBuild;
import hudson.model.TaskListener;
import hudson.plugins.perforce.PerforceSCM;
import org.jfrog.build.vcs.perforce.PerforceClient;

import java.io.IOException;

/**
 * Using the legacy Perforce plugin which is not maintained any more
 *
 * @author Aviad Shikloshi
 */
public class LegacyPerforceManager extends AbstractPerforceManager<PerforceSCM> {

    protected PerforceClient.Builder builder;

    public LegacyPerforceManager(AbstractBuild<?, ?> build, TaskListener buildListener) {
        super(build, buildListener);
    }

    @Override
    public void prepare() throws IOException, InterruptedException {
        builder = new PerforceClient.Builder();
        PerforceSCM jenkinsScm = getJenkinsScm();
        String hostAddress = jenkinsScm.getP4Port();
        if (!hostAddress.contains(":")) {
            hostAddress = "localhost:" + hostAddress;
        }
        builder.hostAddress(hostAddress).client(build.getEnvironment(buildListener).get("P4CLIENT"));
        builder.username(jenkinsScm.getP4User()).password(jenkinsScm.getDecryptedP4Passwd());
        builder.charset(jenkinsScm.getP4Charset());
        perforce = builder.build();
    }

    @Override
    public PerforceClient establishConnection() throws Exception {
        return this.builder.build();
    }
}
