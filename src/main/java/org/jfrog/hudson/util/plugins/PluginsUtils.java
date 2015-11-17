package org.jfrog.hudson.util.plugins;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.*;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import org.acegisecurity.Authentication;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.jfrog.hudson.ArtifactoryBuilder;
import org.jfrog.hudson.util.Credentials;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class PluginsUtils {
    public static final String MULTIJOB_PLUGIN_ID = "jenkins-multijob-plugin";
    public static final String JIRA_REST_SERVERINFO_ENDPOINT = "rest/api/2/serverInfo";

    private static ObjectMapper mapper;

    public static ListBoxModel fillPluginCredentials(Item project) {
        return fillPluginCredentials(project, ACL.SYSTEM);
    }

    public static ListBoxModel fillPluginCredentials(Item project, Authentication authentication) {
        if (project != null && !project.hasPermission(Item.CONFIGURE)) {
            return new StandardListBoxModel();
        }
        List<DomainRequirement> domainRequirements = Collections.emptyList();
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
                CredentialsMatchers.allOf(CredentialsMatchers.withId(credentialsId))
        );
        if (usernamePasswordCredentials != null) {
            return new Credentials(usernamePasswordCredentials.getUsername(),
                    usernamePasswordCredentials.getPassword().getPlainText());
        }
        return Credentials.EMPTY_CREDENTIALS;
    }

    public static boolean isUseCredentialsPlugin() {
        ArtifactoryBuilder.DescriptorImpl descriptor = (ArtifactoryBuilder.DescriptorImpl)
                Hudson.getInstance().getDescriptor(ArtifactoryBuilder.class);
        if (descriptor != null) {
            return descriptor.getUseCredentialsPlugin();
        }
        throw new IllegalStateException("ArtifactoryBuilder descriptor is null");
    }

    /**
     * From Jira plugin version 2.0 (and Jira 7.0) we are not able to retrieve the server info directly from
     * the Java API so we need to access the entry point directly
     *
     * @param jiraBaseUrl is the Jira URL as it was configured in the Jenkins global configuration
     * @return Jira version
     * @throws IOException
     */
    public static String getJiraVersion(URL jiraBaseUrl) {
        HttpClient client = new DefaultHttpClient();
        try {
            URL requestUrl = new URL(jiraBaseUrl + JIRA_REST_SERVERINFO_ENDPOINT);
            HttpResponse response = client.execute(new HttpGet(requestUrl.toURI()));
            lazyInitMapper();
            Map<String, Object> responseMap = mapper.readValue(response.getEntity().getContent(), new TypeReference<Map<String, Object>>() {});
            return ((String) responseMap.get("version"));
        } catch (Exception e) {
            throw new RuntimeException("Error while trying to get Jira Issue Tracker version: " + e.getMessage());
        }
    }

    private static ObjectMapper lazyInitMapper() {
        if (mapper == null) {
            mapper = new ObjectMapper();
        }
        return mapper;
    }
}
