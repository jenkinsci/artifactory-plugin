package org.jfrog.hudson;

import hudson.model.Hudson;
import org.apache.commons.lang.StringUtils;
import org.jfrog.hudson.util.Credentials;
import org.jfrog.hudson.util.plugins.PluginsUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;

/**
 * Configuration for all available credentials providers - Legacy credentials method or credentials plugin
 * Each call for username/password should go through here and this object will provide the username/password
 * according to the global configuration objects which will configured with the "useLegacyCredentials" property
 *
 * @author Aviad Shikloshi
 */
public class CredentialsConfig implements Serializable {

    private boolean useLegacyCredentials = false;

    private Credentials credentials;
    private String credentialsId;

    /**
     * Constructed from the build configuration (Maven, Gradle, Ivy, Freestyle, etc)
     * This object obtains the username, password and credentials id (used with the Credentials plugin)
     * Each of these properties could be empty string but not null
     *
     * @param username      legacy username from textbox
     * @param password      legacy password from textbox
     * @param credentialsId credentialsId chosen from the select box
     */
    @DataBoundConstructor
    public CredentialsConfig(String username, String password, String credentialsId) {
        ArtifactoryBuilder.DescriptorImpl descriptor = (ArtifactoryBuilder.DescriptorImpl)
                Hudson.getInstance().getDescriptor(ArtifactoryBuilder.class);
        this.useLegacyCredentials = descriptor.getUseLegacyCredentials();
        this.credentials = new Credentials(username, password);
        this.credentialsId = credentialsId;
    }

    public CredentialsConfig(Credentials credentials, String credentialsId) {
        this.credentials = credentials;
        this.credentialsId = credentialsId;
    }

    /**
     * In case of overriding the global configuration this method should be called to check if override credentials was supplied
     * from configuration - this will take under considuratino the state of the "useLegacyCredentials" option in global config object
     *
     * @return in legacy mode this will return true if username and password both supplied (todo: check if this is the right logic or should bring back the checkbox),
     * In Credentials plugin mode this will return true if a credentials id was selected in the job configuration
     */
    public boolean isCredentialsProvided() {
        if (useLegacyCredentials) {
            return StringUtils.isNotBlank(credentials.getUsername()) && StringUtils.isNotBlank(credentials.getPassword());
        }
        return StringUtils.isNotBlank(credentialsId);
    }

    /**
     * Not like getUsername this will return the username of the current Credentials mode of the system (legacy/credentials plugin)
     *
     * @return the username that should be apply in this configuration
     */
    public String provideUsername() {
        return useLegacyCredentials ? credentials.getUsername() : PluginsUtils.credentialsLookup(credentialsId).getUsername();
    }

    /**
     * Not like getPassword this will return the username of the current Credentials mode of the system (legacy/credentials plugin)
     *
     * @return the password that should be apply in this configuration
     */
    public String providePassword() {
        return useLegacyCredentials ? credentials.getPassword() : PluginsUtils.credentialsLookup(credentialsId).getPassword();
    }

    public Credentials getCredentials() {
        return useLegacyCredentials ? credentials : PluginsUtils.credentialsLookup(credentialsId);
    }

    // Jenkins Jelly getters for displaying values on user interface

    public String getUsername() {
        if (credentials == null) {
            return StringUtils.EMPTY;
        }
        return credentials.getUsername();
    }

    public String getPassword() {
        if (credentials == null) {
            return StringUtils.EMPTY;
        }
        return credentials.getPassword();
    }

    public String getCredentialsId() {
        return credentialsId;
    }
}
