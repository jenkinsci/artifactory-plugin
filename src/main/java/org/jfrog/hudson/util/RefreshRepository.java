package org.jfrog.hudson.util;

import java.util.List;

/**
 * @author Lior Hasson
 */
public class RefreshRepository<T> {
    private List<T> repos;
    private String responseMessage;
    private boolean success;

    public List<T> getRepos() {
        return repos;
    }

    public void setRepos(List<T> repos) {
        this.repos = repos;
    }

    public String getResponseMessage() {
        return responseMessage;
    }

    public void setResponseMessage(String responseMessage) {
        this.responseMessage = responseMessage;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }
}
