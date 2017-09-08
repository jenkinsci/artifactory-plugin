package org.jfrog.hudson.pipeline.docker.utils;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import hudson.Launcher;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.remoting.Callable;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jfrog.hudson.pipeline.docker.DockerImage;
import org.jfrog.hudson.pipeline.docker.proxy.BuildInfoProxy;
import org.jfrog.hudson.util.JenkinsBuildInfoLog;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by romang on 8/15/16.
 */
public class DockerAgentUtils implements Serializable {
    // Docker images cache. Every image which is intersepted by the Build-Info Proxy, is added to this cache,
    // so that it can be used to create the build-info in Artifactory.
    private static Map<String, DockerImage> images = new ConcurrentHashMap<String, DockerImage>();

    /**
     * Registers an image to be captured by the build-info proxy.
     *
     * @param imageTag
     * @param host
     * @param targetRepo
     * @param buildInfoId
     * @throws IOException
     * @throws InterruptedException
     */
    public synchronized static void registerImagOnAgents(Launcher launcher, final String imageTag,
            final String host, final String targetRepo, final ArrayListMultimap<String, String> artifactsProps,
                final int buildInfoId) throws IOException, InterruptedException {
        // Master
        final String imageId = getImageIdFromAgent(launcher, imageTag, host);
        registerImage(imageId, imageTag, targetRepo, artifactsProps, buildInfoId);

        // Agents
        List<Node> nodes = Jenkins.getInstance().getNodes();
        for (Node node : nodes) {
            if (node == null || node.getChannel() == null) {
                continue;
            }
            node.getChannel().call(new Callable<Boolean, IOException>() {
                public Boolean call() throws IOException {
                    registerImage(imageId, imageTag, targetRepo, artifactsProps, buildInfoId);
                    return true;
                }
            });
        }
    }

    /**
     * Registers an image to the images cache, so that it can be captured by the build-info proxy.
     *
     * @param imageId
     * @param imageTag
     * @param targetRepo
     * @param buildInfoId
     * @throws IOException
     */
    private static void registerImage(String imageId, String imageTag, String targetRepo,
            ArrayListMultimap<String, String> artifactsProps, int buildInfoId) throws IOException {
        DockerImage image = images.get(imageId);
        if (image == null) {
            synchronized (images) {
                image = images.get(imageId);
                if (image == null) {
                    image = new DockerImage(imageId, imageTag, targetRepo);
                    images.put(imageId, image);
                }
            }
        }
        image.addBuildInfoId(buildInfoId);
        image.setArtifactsProps(artifactsProps);
    }

    /**
     * Captures the docker image manifest and extracts the layers data
     * from it, to create a build-info for it.
     *
     * @param content
     * @param properties
     */
    public synchronized static void captureContent(String content, Properties properties) {
        try {
            String digest = DockerUtils.getConfigDigest(content);
            for (Iterator<Map.Entry<String, DockerImage>> it = images.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, DockerImage> entry = it.next();
                DockerImage image = entry.getValue();
                if (digest.equals(image.getImageId())) {
                    image.setManifest(content);
                    image.setAgentName(BuildInfoProxy.getAgentName());
                    image.addBuildInfoModuleProps(properties);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Gets a list of registered docker images from the images cache, if it has been
     * registered to the cache for a specific build-info ID and if a docker manifest has been captured for it
     * by the build-info proxy.
     * @param buildInfoId
     * @return
     */
    public static List<DockerImage> getImagesByBuildId(int buildInfoId) {
        List<DockerImage> list = new ArrayList<DockerImage>();
        for (Iterator<Map.Entry<String, DockerImage>> it = images.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, DockerImage> entry = it.next();
            DockerImage image = entry.getValue();
            if (image.hasBuild(buildInfoId) && image.hasManifest()) {
                list.add(image);
            }
        }
        return list;
    }

    /**
     * Gets a list of registered docker images from the images cache, if it has been
     * registered to the cache for a specific build-info ID and if a docker manifest has been captured for it
     * by the build-info proxy.
     * Additionally, the methods also removes the returned images from the cache.
     * @param buildInfoId
     * @return
     */
    public static List<DockerImage> getAndDiscardImagesByBuildId(int buildInfoId) {
        List<DockerImage> list = new ArrayList<DockerImage>();
        synchronized(images) {
            for (Iterator<Map.Entry<String, DockerImage>> it = images.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, DockerImage> entry = it.next();
                DockerImage image = entry.getValue();
                if (image.hasBuild(buildInfoId)) {
                    if (image.hasManifest()) {
                        list.add(image);
                    }
                    image.removeBuild(buildInfoId);
                }
                if (!image.hasBuilds()) {
                    it.remove();
                }
            }
        }
        return list;
    }

    /**
     * Retrieves from all the Jenkins agents all the docker images, which have been registered for a specific build-info ID
     * Only images for which manifests have been captured are returned.
     *
     * @param buildInfoId
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    public static List<DockerImage> getDockerImagesFromAgents(final int buildInfoId, TaskListener listener) throws IOException, InterruptedException {
        List<DockerImage> dockerImages = new ArrayList<DockerImage>();

        // Collect images from the master:
        dockerImages.addAll(getAndDiscardImagesByBuildId(buildInfoId));

        // Collect images from all the agents:
        List<Node> nodes = Jenkins.getInstance().getNodes();
        for (Node node : nodes) {
            if (node == null || node.getChannel() == null) {
                continue;
            }
            try {
                List<DockerImage> partialDockerImages = node.getChannel().call(new Callable<List<DockerImage>, IOException>() {
                    public List<DockerImage> call() throws IOException {
                        List<DockerImage> dockerImages = new ArrayList<DockerImage>();
                        dockerImages.addAll(getAndDiscardImagesByBuildId(buildInfoId));
                        return dockerImages;
                    }
                });
                dockerImages.addAll(partialDockerImages);
            } catch (Exception e) {
                listener.getLogger().println("Could not collect docker images from Jenkins node '" + node.getDisplayName() + "' due to: " + e.getMessage());
            }
        }
        return dockerImages;
    }

    /**
     * Execute push docker image on agent
     *
     * @param launcher
     * @param log
     * @param imageTag
     * @param username
     * @param password
     * @param host     @return
     * @throws IOException
     * @throws InterruptedException
     */
    public static boolean pushImage(Launcher launcher, final JenkinsBuildInfoLog log, final String imageTag, final String username, final String password, final String host)
            throws IOException, InterruptedException {

        return launcher.getChannel().call(new Callable<Boolean, IOException>() {
            public Boolean call() throws IOException {
                String message = "Pushing image: " + imageTag;
                if (StringUtils.isNotEmpty(host)) {
                    message += " using docker daemon host: " + host;
                }

                log.info(message);
                DockerUtils.pushImage(imageTag, username, password, host);
                return true;
            }
        });
    }

    /**
     * Execute pull docker image on agent
     *
     * @param launcher
     * @param imageTag
     * @param username
     * @param password
     * @param host
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    public static boolean pullImage(Launcher launcher, final String imageTag, final String username, final String password, final String host)
            throws IOException, InterruptedException {

        return launcher.getChannel().call(new Callable<Boolean, IOException>() {
            public Boolean call() throws IOException {
                DockerUtils.pullImage(imageTag, username, password, host);
                return true;
            }
        });
    }

    /**
     * Updates property of parent id for the image provided.
     * Returns false if image was not captured true otherwise.
     *
     * @param log
     * @param imageTag
     * @param host
     * @param buildInfoId
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    public static boolean updateImageParentOnAgents(final JenkinsBuildInfoLog log, final String imageTag, final String host, final int buildInfoId) throws IOException, InterruptedException {
        boolean parentUpdated = updateImageParent(log, imageTag, host, buildInfoId);
        List<Node> nodes = Jenkins.getInstance().getNodes();
        for (Node node : nodes) {
            if (node == null || node.getChannel() == null) {
                continue;
            }
            boolean parentNodeUpdated = node.getChannel().call(new Callable<Boolean, IOException>() {
                public Boolean call() throws IOException {
                    return updateImageParent(log, imageTag, host, buildInfoId);
                }
            });
            parentUpdated = parentUpdated ? parentUpdated : parentNodeUpdated;
        }
        return parentUpdated;
    }

    /**
     * Adds the docker image parent (the base image) as property on the docker image.
     * This property will be later added to the generated build-info deployed to Artifactory.
     * @param log
     * @param imageTag
     * @param host
     * @param buildInfoId
     * @return
     * @throws IOException
     */
    private static boolean updateImageParent(JenkinsBuildInfoLog log, String imageTag, String host, int buildInfoId) throws IOException {
        boolean parentUpdated = false;
        for (DockerImage image : getImagesByBuildId(buildInfoId)) {
            if (image.getImageTag().equals(imageTag)) {
                String parentId = DockerUtils.getParentId(image.getImageId(), host);
                if (StringUtils.isNotEmpty(parentId)) {
                    Properties properties = new Properties();
                    properties.setProperty("docker.image.parent", DockerUtils.getShaValue(parentId));
                    image.addBuildInfoModuleProps(properties);
                }
                log.info("Docker build-info captured on '" + image.getAgentName() + "' agent.");
                parentUpdated = true;
            }
        }
        return parentUpdated;
    }

    /**
     * Get image Id from imageTag using DockerBuildInfoHelper client on agent
     *
     * @param imageTag
     * @return
     */
    private static String getImageIdFromAgent(Launcher launcher, final String imageTag, final String host) throws IOException, InterruptedException {
        return launcher.getChannel().call(new Callable<String, IOException>() {
            public String call() throws IOException {
                return DockerUtils.getImageIdFromTag(imageTag, host);
            }
        });
    }
}