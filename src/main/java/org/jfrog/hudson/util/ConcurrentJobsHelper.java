package org.jfrog.hudson.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class helps manage multi thread jobs such as "Multi configuration project".
 * In here we holds the number of thread involve in the job, and if it was already initialized by own of them.
 *
 * @author Lior Hasson
 */
public class ConcurrentJobsHelper {
    public static ConcurrentHashMap<String, ConcurrentBuild> concurrentBuildHandler = new ConcurrentHashMap<String, ConcurrentBuild>();

    public static ConcurrentBuild createMultiConfBuild(AtomicInteger threadsCounter) {
        return new ConcurrentBuild(threadsCounter);
    }

    public static class ConcurrentBuild {
        private AtomicInteger threadsCounter;
        private AtomicBoolean initialized;

        public ConcurrentBuild(AtomicInteger threadsCounter) {
            this.threadsCounter = threadsCounter;
            this.initialized = new AtomicBoolean(false);
        }

        public AtomicInteger getThreadsCounter() {
            return threadsCounter;
        }

        public AtomicBoolean getInitialized() {
            return initialized;
        }
    }
}
