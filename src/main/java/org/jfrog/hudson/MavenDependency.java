/*
 * Copyright (C) 2010 JFrog Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jfrog.hudson;

import java.io.Serializable;

/**
 * Captures information of a maven dependency.
 *
 * @author Yossi Shaul
 * @deprecated All maven specific classes moved to org.jfrog.hudson.maven.
 */
@Deprecated
public class MavenDependency implements Serializable {
    public String id;
    public String groupId;
    public String artifactId;
    public String version;
    public String type;
    public String classifier;
    public String scope;
    public String fileName;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        MavenDependency that = (MavenDependency) o;

        if (!id.equals(that.id)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
