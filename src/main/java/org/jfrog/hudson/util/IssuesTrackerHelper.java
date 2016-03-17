package org.jfrog.hudson.util;

import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.StreamBuildListener;
import hudson.plugins.jira.JiraIssue;
import hudson.plugins.jira.JiraSite;
import org.jfrog.build.api.Issue;
import org.jfrog.build.api.IssueTracker;
import org.jfrog.build.api.Issues;
import org.jfrog.build.api.IssuesTrackerFields;
import org.jfrog.build.api.builder.BuildInfoBuilder;
import org.jfrog.build.extractor.clientConfiguration.ArtifactoryClientConfiguration;
import org.jfrog.build.extractor.clientConfiguration.util.IssuesTrackerUtils;
import org.jfrog.hudson.util.plugins.PluginsUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @author Noam Y. Tenne
 */
public class IssuesTrackerHelper {

    private String issueTrackerVersion;
    private boolean aggregateBuildIssues;
    private String aggregationBuildStatus;
    private String affectedIssues;
    private String matrixParams;

    public IssuesTrackerHelper(AbstractBuild build, BuildListener listener, boolean aggregateBuildIssues,
            String aggregationBuildStatus) {
        this.aggregateBuildIssues = aggregateBuildIssues;
        this.aggregationBuildStatus = aggregationBuildStatus;
        JiraSite site = JiraSite.get(build.getProject());
        if (site == null) {
                return;
        }

        try {
            issueTrackerVersion = getJiraVersion(site);
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
                affectedIssuesBuilder.append(issueId).append(">>").append(url.toString()).append(">>").append(
                        issue.title);
                matrixParamsBuilder.append(issueId);
            }
            affectedIssues = affectedIssuesBuilder.toString();
            matrixParams = matrixParamsBuilder.toString();
        } catch (Exception e) {
            listener.getLogger()
                    .print("[Warning] Error while trying to collect issue tracker and change information: " +
                            e.getMessage());
        }
    }

    private String getJiraVersion(JiraSite site) {
        return PluginsUtils.getJiraVersion(site.url);
    }


    private Set<String> manuallyCollectIssues(AbstractBuild build, Pattern issuePattern)
            throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Class<?> jiraUpdaterClass = Class.forName("hudson.plugins.jira.Updater");
        Method findIssueIdsRecursive = jiraUpdaterClass.getDeclaredMethod("findIssueIdsRecursive", AbstractBuild.class,
                Pattern.class, BuildListener.class);
        findIssueIdsRecursive.setAccessible(true);
        return (Set<String>) findIssueIdsRecursive.invoke(null, build, issuePattern,
                new StreamBuildListener(ByteStreams.nullOutputStream()));
    }

    /**
     * Applying issues tracker info to a client configuration (used by the extractors)
     */
    public void setIssueTrackerInfo(ArtifactoryClientConfiguration configuration) {
        configuration.info.issues.setIssueTrackerName("JIRA");
        configuration.info.issues.setIssueTrackerVersion(issueTrackerVersion);
        configuration.info.issues.setAggregateBuildIssues(aggregateBuildIssues);
        if (aggregateBuildIssues) {
            configuration.info.issues.setAggregationBuildStatus(aggregationBuildStatus);
        } else {
            configuration.info.issues.setAggregationBuildStatus("");
        }
        configuration.info.issues.setAffectedIssues(affectedIssues);
        configuration.publisher.addMatrixParam(IssuesTrackerFields.AFFECTED_ISSUES, matrixParams);
    }

    /**
     * Apply issues tracker info to a build info builder (used by generic tasks and maven2 which doesn't use the extractor
     */
    public void setIssueTrackerInfo(BuildInfoBuilder builder) {
        Issues issues = new Issues();
        issues.setAggregateBuildIssues(aggregateBuildIssues);
        issues.setAggregationBuildStatus(aggregationBuildStatus);
        issues.setTracker(new IssueTracker("JIRA", issueTrackerVersion));
        Set<Issue> affectedIssuesSet = IssuesTrackerUtils.getAffectedIssuesSet(affectedIssues);
        if (!affectedIssuesSet.isEmpty()) {
            issues.setAffectedIssues(affectedIssuesSet);
        }
        builder.issues(issues);
    }
}
