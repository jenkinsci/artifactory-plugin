package org.jfrog.hudson.util;

import hudson.model.Result;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A helper to manage multi thread jobs such as Multi-Configuration projects.
 *
 * @author Lior Hasson
 */
public class ConcurrentJobsHelper {
    public static ConcurrentHashMap<String, ConcurrentBuild> concurrentBuildHandler = new ConcurrentHashMap<String, ConcurrentBuild>();

    /**
     * The class is used to synchronize the setup stage of jobs which are part of the same multi-configuration project (matrix projects).
     * This is important when we want to make sure that only one of the matrix jobs handles the build initialization
     * and that all other jobs wait till the initialization ends.
     * The project type that chooses to use this utility class should create an instance of this class
     * and implement the setUp() method with the initialization steps.
     * The setUp() method will be invoked by only one job
     * and in addtion, all jobs will wait till the initialization is finished.
     */
    public static abstract class ConcurrentBuildSetupSync {
        public ConcurrentBuildSetupSync(String buildName, int totalBuilds) {
            // Add the build to the concurrent map
            ConcurrentBuild newBuild = new ConcurrentBuild(new AtomicInteger(totalBuilds));
            ConcurrentBuild build = concurrentBuildHandler.putIfAbsent(buildName, newBuild);
            build = build == null ? newBuild : build;

            if (totalBuilds == 1) {
                setUp();
            } else {
                setupMultiBuild(build);
            }
        }

        /**
         * Invokes the setUp() method of only one of the matrix jobs of the build.
         * In addtion, all jobs will wait till the initialization is finished.
         * @param build The build object.
         */
        private void setupMultiBuild(ConcurrentBuild build) {
            // Only one of the matrix jobs should initialize the build:
            if (!build.isInitialized()) {
                synchronized (build) {
                    if (!build.isInitialized()) {
                        setUp();
                        build.setAsInitialized();
                    }
                }
            }
        }

        /**
         * This method should be implemented with the initialization code which we would like
         * to be executed by only one of the matrix jobs.
         */
        public abstract void setUp();
    }

    /**
     * The class is used to synchronize the tearDown (end) stage of jobs which are part of the same multi-configuration project (matrix projects).
     * This is important when we want to make sure that only one of the matrix jobs handles the build tear down and that this job is
     * the last job running.
     * The project type that chooses to use this utility class should create an instance of this class
     * and implement the tearDown() method with the finalization steps.
     * The tearDown() method will be invoked by only the last job running.
     */
    public static abstract class ConcurrentBuildTearDownSync {
        public ConcurrentBuildTearDownSync(String buildName, Result buildResult) {
            ConcurrentBuild build = concurrentBuildHandler.get(buildName);
            if ((build != null && build.getThreadsCounter().decrementAndGet() == 0) || Result.ABORTED.equals(buildResult)) {
                tearDown();
                concurrentBuildHandler.remove(buildName);
            }
        }

        public abstract void tearDown();
    }

    /**
     * Represents a build that its jobs need to be synchronized.
     */
    public static class ConcurrentBuild {
        private AtomicInteger threadsCounter;
        private boolean initialized = false;
        private ConcurrentHashMap<String, String> params = new ConcurrentHashMap<String, String>();

        public ConcurrentBuild(AtomicInteger threadsCounter) {
            this.threadsCounter = threadsCounter;
        }

        public void putParam(String name, String value) {
            params.put(name, value);
        }

        public String getParam(String name) {
            return params.get(name);
        }

        public AtomicInteger getThreadsCounter() {
            return threadsCounter;
        }

        public boolean isInitialized() {
            return initialized;
        }

        public void setAsInitialized() {
            this.initialized = true;
        }
    }
}
