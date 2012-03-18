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

import hudson.model.AbstractBuild;
import hudson.model.TaskListener;
import hudson.scm.SCM;

/**
 * Abstract shared implementation f an {@link ScmManager}.
 *
 * @author Yossi Shaul
 */
public abstract class AbstractScmManager<T extends SCM> implements ScmManager {

    public static final String COMMENT_PREFIX = "[artifactory-release] ";
    protected final AbstractBuild<?, ?> build;
    protected final TaskListener buildListener;

    public AbstractScmManager(AbstractBuild<?, ?> build, TaskListener buildListener) {
        this.build = build;
        this.buildListener = buildListener;
    }

    public T getJenkinsScm() {
        return (T) build.getProject().getRootProject().getScm();
    }

    protected void log(String message) {
        log(buildListener, message);
    }

    protected static void log(TaskListener buildListener, String message) {
        buildListener.getLogger().println("[RELEASE] " + message);
    }
}
