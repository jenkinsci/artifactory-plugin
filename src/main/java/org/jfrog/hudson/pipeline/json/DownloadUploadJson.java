package org.jfrog.hudson.pipeline.json;

/**
 * Created by romang on 4/20/16.
 */
public class DownloadUploadJson {

    private FileJson[] files;

    public FileJson[] getFiles() {
        return files;
    }

    public void setFiles(FileJson[] files) {
        this.files = files;
    }
}
