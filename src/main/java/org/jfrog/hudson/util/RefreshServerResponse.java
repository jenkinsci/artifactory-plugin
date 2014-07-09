package org.jfrog.hudson.util;

import org.jfrog.hudson.VirtualRepository;

import java.util.List;

/**
 * Created by user on 25/06/2014.
 */
public class RefreshServerResponse {
    private List<String> repositories;
    private List<VirtualRepository> virtualRepositories;
    private List<String> userPlugins;
    private String responseMessage;
    private boolean success;

    public List<String> getRepositories() {
        return repositories;
    }

    public void setRepositories(List<String> repositories) {
        this.repositories = repositories;
    }

    public List<VirtualRepository> getVirtualRepositories() {
        return virtualRepositories;
    }

    public void setVirtualRepositories(List<VirtualRepository> virtualRepositories) {
        this.virtualRepositories = virtualRepositories;
    }

    public List<String> getUserPlugins() {
        return userPlugins;
    }

    public void setUserPlugins(List<String> userPlugins) {
        this.userPlugins = userPlugins;
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
