package org.jfrog.hudson.util.publisher;

import hudson.model.Descriptor;
import hudson.tasks.Publisher;
import hudson.util.DescribableList;
import jenkins.model.Jenkins;

/**
 * This factory is responsibly to find the right Publisher {@link hudson.tasks.Publisher} depending
 * on some conditions or restrains.
 *
 * @author Lior Hasson
 */
public class PublisherFactory<T extends Publisher> {

    public static String FLEXIBLE_PLUGIN = "flexible-publish";

    public T create(DescribableList<Publisher, Descriptor<Publisher>> publishersList, Class<T> type) {
        for (Publisher publisher : publishersList) {
            if (type.isInstance(publisher)) {
                return new PublisherArtifactoryRedeploy<T>().find(publisher, type);
            } else if (Jenkins.getInstance().getPlugin(FLEXIBLE_PLUGIN) != null) {
                return new PublisherFlexible<T>().find(publisher, type);
            }
        }

        return null;
    }
}
