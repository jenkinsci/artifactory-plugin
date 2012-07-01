package org.jfrog.hudson.util;

import com.google.common.collect.Sets;
import com.google.common.io.NullOutputStream;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.StreamBuildListener;
import hudson.plugins.jira.JiraIssue;
import hudson.plugins.jira.JiraSession;
import hudson.plugins.jira.JiraSite;
import hudson.plugins.jira.soap.RemoteServerInfo;
import org.jfrog.build.api.IssuesTrackerFields;
import org.jfrog.build.client.ArtifactoryClientConfiguration;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @author Noam Y. Tenne
 */
public class IssuesTrackerHelper {

    public void setIssueTrackerInfo(AbstractBuild build, BuildListener listener,
            ArtifactoryClientConfiguration configuration, boolean aggregateBuildIssues, String aggregationBuildStatus) {
        JiraSite site = JiraSite.get(build.getProject());
        if (site == null) {
            return;
        }

        try {
            configuration.info.issues.setIssueTrackerName("JIRA");
            JiraSession session = site.createSession();
            RemoteServerInfo info = session.service.getServerInfo(session.token);
            configuration.info.issues.setIssueTrackerVersion(info.getVersion());
            configuration.info.issues.setAggregateBuildIssues(aggregateBuildIssues);
            if (aggregateBuildIssues) {
                configuration.info.issues.setAggregationBuildStatus(aggregationBuildStatus);
            } else {
                configuration.info.issues.setAggregationBuildStatus("");
            }

            StringBuilder affectedIssuesBuilder = new StringBuilder();
            StringBuilder matrixParamsBuilder = new StringBuilder();
            Set<String> issueIds = Sets.newHashSet(manuallyCollectIssues(build, site.getIssuePattern()));
            for (String issueId : issueIds) {
                if (!site.existsIssue(issueId)) {
                    continue;
                }

                if (affectedIssuesBuilder.length() > 0) {
                    affectedIssuesBuilder.append(",");
                    matrixParamsBuilder.append(",");
                }

                URL url = site.getUrl(issueId);
                JiraIssue issue = site.getIssue(issueId);
                affectedIssuesBuilder.append(issueId).append(">>").append(url.toString()).append(">>")
                        .append(issue.title);
                matrixParamsBuilder.append(issueId);
            }
            configuration.info.issues.setAffectedIssues(affectedIssuesBuilder.toString());
            configuration.publisher
                    .addMatrixParam(IssuesTrackerFields.AFFECTED_ISSUES, matrixParamsBuilder.toString());
        } catch (Exception e) {
            listener.getLogger()
                    .print("[Warning] Error while trying to collect issue tracker and change information: " +
                            e.getMessage());
        }
    }

    private Set<String> manuallyCollectIssues(AbstractBuild build, Pattern issuePattern)
            throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Class<?> jiraUpdaterClass = Class.forName("hudson.plugins.jira.Updater");
        Method findIssueIdsRecursive = jiraUpdaterClass.getDeclaredMethod("findIssueIdsRecursive", AbstractBuild.class,
                Pattern.class, BuildListener.class);
        findIssueIdsRecursive.setAccessible(true);
        return (Set<String>) findIssueIdsRecursive.invoke(null, build, issuePattern,
                new StreamBuildListener(new NullOutputStream()));
    }
}
