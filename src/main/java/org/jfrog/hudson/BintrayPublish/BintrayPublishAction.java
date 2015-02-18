package org.jfrog.hudson.BintrayPublish;

import com.google.common.collect.Lists;
import hudson.model.*;
import hudson.security.ACL;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpResponse;
import org.jfrog.build.api.builder.BintrayUploadInfoBuilder;
import org.jfrog.build.api.release.BintrayUploadInfoOverride;
import org.jfrog.build.client.ArtifactoryVersion;
import org.jfrog.build.extractor.clientConfiguration.client.ArtifactoryBuildInfoClient;
import org.jfrog.hudson.ArtifactoryServer;
import org.jfrog.hudson.BuildInfoAwareConfigurator;
import org.jfrog.hudson.DeployerOverrider;
import org.jfrog.hudson.util.CredentialResolver;
import org.jfrog.hudson.util.Credentials;
import org.jfrog.hudson.util.ExtractorUtils;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Aviad Shikloshi
 */
public class BintrayPublishAction<C extends BuildInfoAwareConfigurator & DeployerOverrider> extends TaskAction implements BuildBadgeAction {

    private static Map<String, String> signMethodMap;

    static {
        signMethodMap = new HashMap<String, String>();
        signMethodMap.put("Sign", "true");
        signMethodMap.put("Don't sign", "false");
        signMethodMap.put("Use descriptor", "");
    }

    private final AbstractBuild build;
    private final C configurator;

    private String subject;
    private String repoName;
    private String packageName;
    private String versionName;
    private String signMethod;
    private List<String> licenses;
    private String passphrase;

    public BintrayPublishAction(AbstractBuild build, C configurator) {
        this.build = build;
        this.configurator = configurator;
    }

    public void doSubmit(StaplerRequest req, StaplerResponse resp) throws ServletException, IOException {
        ArtifactoryServer artifactory = configurator.getArtifactoryServer();

        User user = User.current();
        String ciUser = (user == null) ? "anonymous" : user.getId();
        req.bindParameters(this);
        Credentials credentials = CredentialResolver.getPreferredDeployer(configurator, configurator.getArtifactoryServer());

        new PushToBintrayWorker(artifactory, credentials, ciUser).start();
        resp.sendRedirect("."); // where does that redirect to ?
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void doIndex(StaplerRequest req, StaplerResponse resp) throws IOException, ServletException {
        req.getView(this, getCurrentAction()).forward(req, resp);
    }

    public boolean hasPushToBintrayPermission() {
        // TODO: should implement
        return true;
    }

    public synchronized String getCurrentAction() {
        return workerThread == null ? "form.jelly" : "progress.jelly";
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getRepoName() {
        return repoName;
    }

    public void setRepoName(String repoName) {
        this.repoName = repoName;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getVersionName() {
        return versionName;
    }

    public void setVersionName(String versionName) {
        this.versionName = versionName;
    }

    public String getSignMethod() {
        return signMethod;
    }

    public void setSignMethod(String signMethod) {
        this.signMethod = signMethod;
    }

    public List<String> getSignMethods() {
        return Lists.newArrayList("Use descriptor", "Sign", "Don't sign");
    }

    public String getLicenses() {
        return StringUtils.join(licenses, ",");
    }

    public void setLicenses(String licenses) {
        this.licenses = Lists.newArrayList(licenses.split(","));
    }

    public String getPassphrase() {
        return passphrase;
    }

    public void setPassphrase(String passphrase) {
        this.passphrase = passphrase;
    }

    public AbstractBuild getBuild() {
        return build;
    }

    @Override
    protected Permission getPermission() {
        return null;
    }

    @Override
    protected ACL getACL() {
        return build.getACL();
    }

    public String getIconFileName() {
        return "/plugin/artifactory/images/bintray.png";
    }

    public String getBadgeFileName() {
        return "/plugin/artifactory/images/bintray-badge.png";
    }

    public String getDisplayName() {
        return "Push to Bintray";
    }

    public String getUrlName() {
        if (hasPushToBintrayPermission())
            return "bintray";
        else
            return null;
    }

    public final class PushToBintrayWorker extends TaskThread {

        private final ArtifactoryServer artifactoryServer;
        private final Credentials deployer;
        //private final String user;

        public PushToBintrayWorker(ArtifactoryServer artifactoryServer, Credentials deployer, String user) {
            super(BintrayPublishAction.this, ListenerAndText.forMemory(null));
            this.artifactoryServer = artifactoryServer;
            this.deployer = deployer;
            //this.user = user;
        }

        @Override
        protected void perform(TaskListener listener) throws IOException {

            PrintStream logger = listener.getLogger();
            logger.println("Publishing to Bintray...");

            ArtifactoryBuildInfoClient client =
                    artifactoryServer.createArtifactoryClient(deployer.getUsername(), deployer.getPassword(),
                            artifactoryServer.createProxyConfiguration(Jenkins.getInstance().proxy));

            if (!isValidArtifactoryVersion(client, logger)) {
                logger.println("Bintray push is supported from Artifactory 3.5 version.");
                return;
            }

            BintrayUploadInfoBuilder builder = new BintrayUploadInfoBuilder();
            BintrayUploadInfoOverride uploadInfo =
                    builder.setSubject(subject).setVersionName(versionName).setLicenses(licenses)
                            .setPackageName(packageName).setRepoName(repoName).build();
            if (!uploadInfo.isValid()) {
                logger.println("Upload info is invalid.");
                return;
            }

            String buildName = ExtractorUtils.sanitizeBuildName(build.getParent().getName());
            String buildNumber = Integer.toString(build.getNumber());

            HttpResponse response = client.publishToBintray(buildName, buildNumber, signMethodMap.get(signMethod),
                    passphrase, uploadInfo);

            logger.println(response);
            workerThread = null;
            client.shutdown();
        }

        private boolean isValidArtifactoryVersion(ArtifactoryBuildInfoClient client, PrintStream logger) {
            boolean validVersion = false;
            try {
                ArtifactoryVersion version = client.verifyCompatibleArtifactoryVersion();
                validVersion = version.isAtLeast(new ArtifactoryVersion("3.5"));
            } catch (Exception e) {
                e.printStackTrace();
                logger.println("Error while checking current Artifactory version");
            }
            return validVersion;
        }
    }

}