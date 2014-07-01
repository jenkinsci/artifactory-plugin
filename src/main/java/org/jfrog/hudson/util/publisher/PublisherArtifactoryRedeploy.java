package org.jfrog.hudson.util.publisher;

import hudson.tasks.Publisher;

/**
 * Responsibly to find Artifactory deployment publisher {@link org.jfrog.hudson.ArtifactoryRedeployPublisher}
 *
 * @author Lior Hasson
 */
public class PublisherArtifactoryRedeploy<T extends Publisher> implements PublisherFind<T> {

    public T find(Publisher publisher, Class<T> type) {
        if (type.isInstance(publisher))
            return type.cast(publisher);

        return null;
    }
}
