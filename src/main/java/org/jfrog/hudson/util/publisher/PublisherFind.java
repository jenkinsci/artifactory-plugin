package org.jfrog.hudson.util.publisher;

import hudson.tasks.Publisher;

/**
 * @author Lior Hasson
 */
public interface PublisherFind<T extends Publisher> {

    T find(Publisher publisher, Class<T> type);
}
