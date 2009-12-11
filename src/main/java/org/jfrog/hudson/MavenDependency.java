package org.jfrog.hudson;

import java.io.Serializable;

/**
 * Captures information of a maven dependency.
 *
 * @author Yossi Shaul
 */
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
