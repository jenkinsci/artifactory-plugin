package org.jfrog.hudson.pipeline.json;

/**
 * Created by romang on 4/20/16.
 */
public class FileJson {
    private AqlJson aql;
    private String pattern;
    private String target;
    private String props;
    private String recursive;
    private String flat;


    public String getAql() {
        if (aql != null) {
            return aql.getFind();
        }
        return null;
    }

    public String getPattern() {
        return pattern;
    }

    public String getTarget() {
        return target;
    }

    public void setAql(AqlJson aql) {
        this.aql = aql;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getProps() {
        return props;
    }

    public void setProps(String props) {
        this.props = props;
    }

    public String getRecursive() {
        return recursive;
    }

    public void setRecursive(String recursive) {
        this.recursive = recursive;
    }

    public String getFlat() {
        return flat;
    }

    public void setFlat(String flat) {
        this.flat = flat;
    }
}
