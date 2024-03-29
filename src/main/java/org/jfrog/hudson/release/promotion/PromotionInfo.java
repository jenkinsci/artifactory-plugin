package org.jfrog.hudson.release.promotion;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.jfrog.hudson.BuildInfoAwareConfigurator;

import java.io.Serializable;

/**
 * Created by yahavi on 14/03/2017.
 */
@SuppressFBWarnings(value = "SE_BAD_FIELD")
public class PromotionInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    private PromotionConfig promotionConfig;
    private BuildInfoAwareConfigurator configurator;
    private String id;
    private String displayName;

    PromotionInfo(PromotionConfig promotionConfig, BuildInfoAwareConfigurator configurator, int id, String displayName) {
        this.promotionConfig = promotionConfig;
        this.configurator = configurator;
        this.id = String.valueOf(id);
        this.displayName = displayName;
    }

    public PromotionConfig getPromotionConfig() {
        return this.promotionConfig;
    }

    public String getBuildName() {
        return this.promotionConfig.getBuildName();
    }

    public String getBuildNumber() {
        return this.promotionConfig.getBuildNumber();
    }

    public String getProject() {
        return this.promotionConfig.getProject();
    }

    public BuildInfoAwareConfigurator getConfigurator() {
        return this.configurator;
    }

    public String getUrl() {
        return this.configurator.getArtifactoryServer().getArtifactoryUrl();
    }

    public String getId() {
        return this.id;
    }

    public String getStatus() {
        return this.promotionConfig.getStatus();
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public String getDisplayName() {
        if (this.displayName == null) {
            return getBuildName() + "/" + getBuildNumber() + " " + getUrl();
        }
        return this.displayName;
    }
}