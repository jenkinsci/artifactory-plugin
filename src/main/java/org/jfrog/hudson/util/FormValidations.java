package org.jfrog.hudson.util;

import com.google.common.base.Strings;
import hudson.util.FormValidation;
import org.apache.commons.lang.StringUtils;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

/**
 * Utility class for form validations.
 *
 * @author Yossi Shaul
 */
public abstract class FormValidations {
    /**
     * Validates a space separated list of emails.
     *
     * @param emails Space separated list of emails
     * @return {@link hudson.util.FormValidation.ok()} if valid or empty, error otherwise
     */
    public static FormValidation validateEmails(String emails) {
        if (!Strings.isNullOrEmpty(emails)) {
            String[] recipients = StringUtils.split(emails, " ");
            for (String email : recipients) {
                FormValidation validation = validateInternetAddress(email);
                if (validation != FormValidation.ok()) {
                    return validation;
                }
            }
        }
        return FormValidation.ok();
    }

    /**
     * Validates an internet address (url, email address, etc.).
     *
     * @param address The address to validate
     * @return {@link hudson.util.FormValidation.ok()} if valid or empty, error otherwise
     */
    public static FormValidation validateInternetAddress(String address) {
        if (Strings.isNullOrEmpty(address)) {
            return FormValidation.ok();
        }
        try {
            new InternetAddress(address);
            return FormValidation.ok();
        } catch (AddressException e) {
            return FormValidation.error(e.getMessage());
        }
    }
}
