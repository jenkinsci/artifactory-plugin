package org.jfrog.hudson.util.publisher;

import hudson.model.AbstractProject;
import hudson.tasks.Publisher;
import org.jenkins_ci.plugins.flexible_publish.ConditionalPublisher;
import org.jenkins_ci.plugins.flexible_publish.FlexiblePublisher;

import java.util.List;

/**
 * Responsibly to find Flexible deployment publisher {@link org.jenkins_ci.plugins.flexible_publish.FlexiblePublisher}
 *
 * @author Lior Hasson
 */
public class PublisherFlexible<T extends Publisher> implements PublisherFind<T> {


    public T find(Publisher publisher, Class<T> type) {
        if (publisher instanceof FlexiblePublisher) {
            List<ConditionalPublisher> conditions = ((FlexiblePublisher) publisher).getPublishers();
            for (ConditionalPublisher condition : conditions) {
                if (type.isInstance(condition.getPublisher())) {
                    return type.cast(condition.getPublisher());
                }
            }
        }

        return null;
    }

    public boolean isPublisherWrapped(AbstractProject<?, ?> project, Class<T> type) {
        for (Publisher publisher : project.getPublishersList()) {
            if (find(publisher, type) != null)
                return true;
        }

        return false;
    }
}
