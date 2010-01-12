package org.jfrog.hudson;

import hudson.util.Scrambler;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents an artifactory instance.
 *
 * @author Yossi Shaul
 */
public class ArtifactoryServer {
    private static final Logger log = Logger.getLogger(ArtifactoryServer.class.getName());

    private static final String LOCAL_REPOS_REST_RUL = "/api/repositories?repoType=local";
    private static final int DEFAULT_CONNECTION_TIMEOUT = 120000;    // 2 Minutes

    private final String url;
    private final String userName;
    private final String password;    // base64 scrambled password
    // Network timeout in milliseconds to use both for connection establishment and for unanswered requests
    private int timeout = DEFAULT_CONNECTION_TIMEOUT;

    /**
     * List of repository keys, last time we checked. Copy on write semantics.
     */
    private transient volatile List<String> repositories;

    @DataBoundConstructor
    public ArtifactoryServer(String url, String userName, String password, int timeout) {
        this.url = StringUtils.removeEnd(url, "/");
        this.userName = userName;
        this.password = Scrambler.scramble(password);
        this.timeout = timeout > 0 ? timeout : DEFAULT_CONNECTION_TIMEOUT;
    }

    public String getName() {
        return url;
    }

    public String getUrl() {
        return url;
    }

    public String getUserName() {
        return userName;
    }

    public String getPassword() {
        return Scrambler.descramble(password);
    }

    public int getTimeout() {
        return timeout;
    }

    public List<String> getRepositoryKeys() {
        try {
            PreemptiveHttpClient httpclient = createHttpClient(userName, getPassword());

            String localReposUrl = url + LOCAL_REPOS_REST_RUL;
            HttpGet httpget = new HttpGet(localReposUrl);
            HttpResponse response = httpclient.execute(httpget);
            if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                log.warning("Failed to obtain list of repositories: " + response.getStatusLine());
                repositories = Collections.emptyList();
            } else {
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    repositories = new ArrayList<String>();
                    String result = IOUtils.toString(entity.getContent());
                    log.fine("repositories result = " + result);
                    JSONArray jsonArray = JSONArray.fromObject(result);
                    for (Object o : jsonArray) {
                        JSONObject jsonObject = (JSONObject) o;
                        String repositoryKey = jsonObject.getString("key");
                        repositories.add(repositoryKey);
                    }
                }
            }
        } catch (IOException e) {
            log.log(Level.WARNING, "Failed to obtain list of repositories: " + e.getMessage());
        }

        return repositories;
    }

    public PreemptiveHttpClient createHttpClient(String userName, String password) {
        return new PreemptiveHttpClient(userName, password, timeout);
    }
}
