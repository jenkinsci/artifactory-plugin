package org.jfrog.hudson.release.gradle;

import hudson.model.AbstractProject;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.servlet.ServletException;
import java.io.IOException;

/**
 * This class is used for managing the Artifactory Release Staging functionality for Gradle projects using the Artifactory
 * Release Staging API.
 * The API is invoked using a URL with the following pattern:
 * <Jenkins server>/Jenkins>/job/<Project>/artifactory/staging?<Arguments List>
 */
public class GradleReleaseApiAction extends BaseGradleReleaseAction {

    public GradleReleaseApiAction(AbstractProject<?, ?> project) {
        super(project);
    }

    public String getIconFileName() {
        return null;
    }

    public String getUrlName() {
        return "artifactory";
    }

    /**
     */
    @SuppressWarnings({"UnusedDeclaration"})
    @RequirePOST
    public void doStaging(StaplerRequest req, StaplerResponse resp) throws IOException, ServletException {
        doApi(req, resp);
    }
}
