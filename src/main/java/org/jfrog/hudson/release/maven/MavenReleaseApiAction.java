package org.jfrog.hudson.release.maven;

import hudson.maven.MavenModuleSet;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import javax.servlet.ServletException;
import java.io.IOException;

/**
 * Created by user on 14/08/2014.
 */
public class MavenReleaseApiAction extends BaseMavenReleaseAction {
    public MavenReleaseApiAction(MavenModuleSet project) {
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
