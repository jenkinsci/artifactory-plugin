package org.jfrog.hudson.util.plugins;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.*;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.jfrog.hudson.ArtifactoryBuilder;
import org.jfrog.hudson.util.Credentials;

import java.util.Collections;
import java.util.List;

public class PluginsUtils {
    public static final String MULTIJOB_PLUGIN_ID = "jenkins-multijob-plugin";

    public static ListBoxModel fillPluginCredentials(Item project) {
        return fillPluginCredentials(project, Jenkins.getAuthentication());
    }

    public static ListBoxModel fillPluginCredentials(Item project, Authentication authentication) {
        if (project != null && !project.hasPermission(Item.CONFIGURE)) {
            return new StandardListBoxModel();
        }
        List<DomainRequirement> domainRequirements = Collections.emptyList();
//        ((DeployerOverrider)((FreeStyleProject) project).buildWrappers.get(0)).getDeployerCredentialsConfig()
        return new StandardListBoxModel()
                .withEmptySelection()
                .withMatching(
                        CredentialsMatchers.anyOf(
                                CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class),
                                CredentialsMatchers.instanceOf(StandardCertificateCredentials.class)
                        ),
                        CredentialsProvider.lookupCredentials(StandardCredentials.class,
                                project,
                                authentication,
                                domainRequirements)
                );
    }

    public static Credentials credentialsLookup(String credentialsId) {
        Item dummy = null;
        UsernamePasswordCredentials usernamePasswordCredentials = CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(
                        UsernamePasswordCredentials.class, dummy, ACL.SYSTEM,
                        Collections.<DomainRequirement>emptyList()),
                CredentialsMatchers.allOf(
                        CredentialsMatchers.withId(credentialsId))
        );

        return new Credentials(usernamePasswordCredentials.getUsername(),
                usernamePasswordCredentials.getPassword().getPlainText());
    }

    public static boolean isUseLegacyCredentials() {
        ArtifactoryBuilder.DescriptorImpl descriptor = (ArtifactoryBuilder.DescriptorImpl)
                Hudson.getInstance().getDescriptor(ArtifactoryBuilder.class);
        return descriptor != null && descriptor.getUseLegacyCredentials();
    }
}
