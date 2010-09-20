package org.jfrog.hudson;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Represents a holder object for notifications, right now only holds recipients for violations.
 *
 * @author Tomer Cohen
 */
public class Notifications {
    private String violationRecipients;

    @DataBoundConstructor
    public Notifications(String violationRecipients) {
        this.violationRecipients = violationRecipients;
    }

    public String getViolationRecipients() {
        return violationRecipients;
    }

    public void setViolationRecipients(String violationRecipients) {
        this.violationRecipients = violationRecipients;
    }
}
