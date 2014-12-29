package org.jfrog.hudson.util.publisher;

import hudson.model.AbstractProject;
import hudson.tasks.Publisher;

/**
 * Created by user on 29/12/2014.
 */
public class PublisherFindImpl<T extends Publisher> implements PublisherFind<T> {
    public T find(AbstractProject<?, ?> project, Class<T> type) {
        for (Publisher publisher : project.getPublishersList()) {
            if (type.isInstance(publisher)) {
                return type.cast(publisher);
            }
        }
        return null;
    }
}
