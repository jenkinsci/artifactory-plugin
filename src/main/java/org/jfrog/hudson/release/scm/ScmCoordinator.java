/*
 * Copyright (C) 2011 JFrog Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jfrog.hudson.release.scm;

import hudson.FilePath;

import java.io.IOException;

/**
 * Base interface for specific scm coordinators.
 *
 * @author Yossi Shaul
 */
public interface ScmCoordinator {
    /**
     * Called immediately after the coordinator is created.
     */
    void prepare() throws IOException, InterruptedException;

    /**
     * Called before changing to release version.
     */
    void beforeReleaseVersionChange() throws IOException, InterruptedException;

    /**
     * Called after a change to release version.
     *
     * @param modified
     */
    void afterReleaseVersionChange(boolean modified) throws IOException, InterruptedException;

    /**
     * Called after a successful release build.
     */
    void afterSuccessfulReleaseVersionBuild() throws Exception;

    /**
     * Called before changing to next development version.
     */
    void beforeDevelopmentVersionChange() throws IOException, InterruptedException;

    /**
     * Called after a change to the next development version.
     *
     * @param modified
     */
    void afterDevelopmentVersionChange(boolean modified) throws IOException, InterruptedException;

    /**
     * Called after the build has completed and the result was finalized.
     */
    void buildCompleted() throws Exception;

    /**
     * Called before a file is modified.
     *
     * @param filePath The file path that is about to be modified.
     */
    void edit(FilePath filePath) throws IOException, InterruptedException;

    String getRemoteUrlForPom();
}
