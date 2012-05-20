package org.jfrog.hudson;

import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.Cause;
import org.apache.commons.lang.StringUtils;
import org.jfrog.build.api.*;
import org.jfrog.build.api.builder.BuildInfoBuilder;
import org.jfrog.build.api.builder.PromotionStatusBuilder;
import org.jfrog.build.api.release.Promotion;
import org.jfrog.hudson.action.ActionableHelper;
import org.jfrog.hudson.release.ReleaseAction;
import org.jfrog.hudson.util.BuildRetentionFactory;
import org.jfrog.hudson.util.ExtractorUtils;

import java.util.Calendar;
import java.util.Map;

/**
 * Handles build info creation and deployment
 *
 * @author Shay Yaakov
 */
public class AbstractBuildInfoDeployer {
    private BuildInfoAwareConfigurator configurator;
    protected AbstractBuild build;
    private EnvVars env;

    public AbstractBuildInfoDeployer(BuildInfoAwareConfigurator configurator, AbstractBuild build, EnvVars env) {
        this.configurator = configurator;
        this.build = build;
        this.env = env;
    }

    protected Build createBuildInfo(String buildAgentName, String buildAgentVersion, BuildType buildType) {
        BuildInfoBuilder builder = new BuildInfoBuilder(ExtractorUtils.sanitizeBuildName(build.getParent().getFullName()))
                .number(build.getNumber() + "").type(buildType)
                .buildAgent(new BuildAgent(buildAgentName, buildAgentVersion))
                .agent(new Agent("hudson", build.getHudsonVersion()));
        String buildUrl = ActionableHelper.getBuildUrl(build);
        if (StringUtils.isNotBlank(buildUrl)) {
            builder.url(buildUrl);
        }

        Calendar startedTimestamp = build.getTimestamp();
        builder.startedDate(startedTimestamp.getTime());

        long duration = System.currentTimeMillis() - startedTimestamp.getTimeInMillis();
        builder.durationMillis(duration);

        String artifactoryPrincipal = configurator.getArtifactoryServer().getResolvingCredentials().getUsername();
        if (StringUtils.isBlank(artifactoryPrincipal)) {
            artifactoryPrincipal = "";
        }
        builder.artifactoryPrincipal(artifactoryPrincipal);

        String userCause = ActionableHelper.getUserCausePrincipal(build);
        if (userCause != null) {
            builder.principal(userCause);
        }

        Cause.UpstreamCause parent = ActionableHelper.getUpstreamCause(build);
        if (parent != null) {
            String parentProject = ExtractorUtils.sanitizeBuildName(parent.getUpstreamProject());
            int parentNumber = parent.getUpstreamBuild();
            builder.parentName(parentProject);
            builder.parentNumber(parentNumber + "");
            if (StringUtils.isBlank(userCause)) {
                builder.principal("auto");
            }
        }

        gatherSysPropInfo(builder);
        addBuildInfoVariables(builder);

        String revision = ExtractorUtils.getVcsRevision(env);
        if (StringUtils.isNotBlank(revision)) {
            builder.vcsRevision(revision);
        }
        if (configurator.isIncludeEnvVars()) {
            for (Map.Entry<String, String> entry : env.entrySet()) {
                builder.addProperty(BuildInfoProperties.BUILD_INFO_ENVIRONMENT_PREFIX + entry.getKey(),
                        entry.getValue());
            }
        } else {
            MapDifference<String, String> difference = Maps.difference(env, System.getenv());
            Map<String, String> filteredEnvVars = difference.entriesOnlyOnLeft();
            for (Map.Entry<String, String> entry : filteredEnvVars.entrySet()) {
                builder.addProperty(BuildInfoProperties.BUILD_INFO_ENVIRONMENT_PREFIX + entry.getKey(),
                        entry.getValue());
            }
        }

        LicenseControl licenseControl = new LicenseControl(configurator.isRunChecks());
        if (configurator.isRunChecks()) {
            if (StringUtils.isNotBlank(configurator.getViolationRecipients())) {
                licenseControl.setLicenseViolationsRecipientsList(configurator.getViolationRecipients());
            }
            if (StringUtils.isNotBlank(configurator.getScopes())) {
                licenseControl.setScopesList(configurator.getScopes());
            }
        }
        licenseControl.setIncludePublishedArtifacts(configurator.isIncludePublishArtifacts());
        licenseControl.setAutoDiscover(configurator.isLicenseAutoDiscovery());
        builder.licenseControl(licenseControl);
        BuildRetention buildRetention = new BuildRetention(configurator.isDiscardBuildArtifacts());
        if (configurator.isDiscardOldBuilds()) {
            buildRetention = BuildRetentionFactory.createBuildRetention(build, configurator.isDiscardBuildArtifacts());
        }
        builder.buildRetention(buildRetention);

        // add staging status if it is a release build
        ReleaseAction release = ActionableHelper.getLatestAction(build, ReleaseAction.class);
        if (release != null) {
            String stagingRepoKey = release.getStagingRepositoryKey();
            if (StringUtils.isBlank(stagingRepoKey)) {
                stagingRepoKey = configurator.getRepositoryKey();
            }
            builder.addStatus(new PromotionStatusBuilder(Promotion.STAGED)
                    .timestampDate(startedTimestamp.getTime())
                    .comment(release.getStagingComment())
                    .repository(stagingRepoKey)
                    .ciUser(userCause).user(artifactoryPrincipal).build());
        }

        Build buildInfo = builder.build();
        // for backwards compatibility for Artifactory 2.2.3
        if (parent != null) {
            buildInfo.setParentBuildId(parent.getUpstreamProject());
        }

        return buildInfo;
    }

    private void gatherSysPropInfo(BuildInfoBuilder infoBuilder) {
        infoBuilder.addProperty("os.arch", System.getProperty("os.arch"));
        infoBuilder.addProperty("os.name", System.getProperty("os.name"));
        infoBuilder.addProperty("os.version", System.getProperty("os.version"));
        infoBuilder.addProperty("java.version", System.getProperty("java.version"));
        infoBuilder.addProperty("java.vm.info", System.getProperty("java.vm.info"));
        infoBuilder.addProperty("java.vm.name", System.getProperty("java.vm.name"));
        infoBuilder.addProperty("java.vm.specification.name", System.getProperty("java.vm.specification.name"));
        infoBuilder.addProperty("java.vm.vendor", System.getProperty("java.vm.vendor"));
    }

    private void addBuildInfoVariables(BuildInfoBuilder infoBuilder) {
        Map<String, String> buildVariables = build.getBuildVariables();
        for (Map.Entry<String, String> entry : buildVariables.entrySet()) {
            infoBuilder.addProperty(
                    BuildInfoProperties.BUILD_INFO_ENVIRONMENT_PREFIX + entry.getKey(), entry.getValue());
        }
    }
}
