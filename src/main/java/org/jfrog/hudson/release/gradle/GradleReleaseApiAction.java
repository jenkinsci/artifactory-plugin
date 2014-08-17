package org.jfrog.hudson.release.gradle;

import hudson.model.FreeStyleProject;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;

/**
 * Created by user on 17/08/2014.
 */
public class GradleReleaseApiAction extends BaseGradleReleaseAction {

    public GradleReleaseApiAction(FreeStyleProject project) {
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
    public void doStaging(StaplerRequest req, StaplerResponse resp) throws IOException, ServletException {
        doApi(req, resp);
    }
}
