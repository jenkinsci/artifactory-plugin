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

import java.io.IOException;

/**
 * Base interface for specific scm coordinators.
 *
 * @author Yossi Shaul
 */
public interface ScmCoordinator {
    void prepare() throws IOException, InterruptedException;

    void afterSuccessfulReleaseVersionBuild() throws InterruptedException, IOException;

    void afterDevelopmentVersionChange() throws IOException, InterruptedException;

    void buildCompleted() throws IOException, InterruptedException;

    String getRemoteUrlForPom();
}
