package org.jfrog.hudson.util.publisher;

import hudson.model.AbstractProject;
import hudson.tasks.Publisher;

/**
 * @author Lior Hasson
 */
public interface PublisherFind<T extends Publisher> {

    T find(AbstractProject<?, ?> project, Class<T> type);
}
