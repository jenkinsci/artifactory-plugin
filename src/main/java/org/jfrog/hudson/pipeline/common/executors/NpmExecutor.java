package org.jfrog.hudson.pipeline.common.executors;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;
import org.apache.commons.lang.StringUtils;
import org.jfrog.build.api.BuildInfoFields;
import org.jfrog.hudson.pipeline.common.Utils;
import org.jfrog.hudson.pipeline.common.types.buildInfo.BuildInfo;
import org.jfrog.hudson.pipeline.common.types.deployers.Deployer;
import org.jfrog.hudson.pipeline.common.types.builds.NpmBuild;
import org.jfrog.hudson.pipeline.common.types.resolvers.Resolver;
import org.jfrog.hudson.util.ExtractorUtils;
import org.jfrog.hudson.util.PluginDependencyHelper;

import java.io.File;
import java.io.IOException;

/**
 * @author yahavi
 */
public abstract class NpmExecutor implements Executor {

    TaskListener listener;
    BuildInfo buildInfo;
    Launcher launcher;
    NpmBuild npmBuild;
    String javaArgs;
    String npmExe;
    FilePath ws;
    String path;
    EnvVars env;
    Run build;

    public NpmExecutor(BuildInfo buildInfo, Launcher launcher, NpmBuild npmBuild, String javaArgs, String npmExe, FilePath ws, String path, EnvVars env, TaskListener listener, Run build) {
        this.listener = listener;
        this.buildInfo = Utils.prepareBuildinfo(build, buildInfo);
        this.launcher = launcher;
        this.npmBuild = npmBuild;
        this.javaArgs = javaArgs;
        this.npmExe = npmExe;
        this.ws = ws;
        this.path = path;
        this.env = env;
        this.build = build;
    }

    public BuildInfo getBuildInfo() {
        return buildInfo;
    }

    public void execute(Deployer deployer, Resolver resolver, String args, String classToExecute) throws Exception {
        ExtractorUtils.addVcsDetailsToEnv(new FilePath(ws, path), env, listener);
        FilePath tempDir = ExtractorUtils.createAndGetTempDir(ws);
        EnvExtractor envExtractor = new NpmEnvExtractor(build,
                buildInfo, deployer, resolver, listener, launcher, tempDir, env, args, path, npmExe);
        envExtractor.execute();
        String absoluteDependencyDirPath = copyExtractorJars(tempDir);
        Utils.launch("npm", launcher, getArgs(absoluteDependencyDirPath, classToExecute), env, listener, ws);
        String generatedBuildPath = env.get(BuildInfoFields.GENERATED_BUILD_INFO);
        buildInfo.append(Utils.getGeneratedBuildInfo(build, listener, launcher, generatedBuildPath));
        buildInfo.setAgentName(Utils.getAgentName(ws));
    }

    private String copyExtractorJars(FilePath tempDir) throws IOException, InterruptedException {
        File extractorJar = PluginDependencyHelper.getExtractorJar(env);
        FilePath dependencyDir = PluginDependencyHelper.getActualDependencyDirectory(extractorJar, tempDir);
        String absoluteDependencyDirPath = dependencyDir.getRemote();
        return absoluteDependencyDirPath.replace("\\", "/");
    }

    private ArgumentListBuilder getArgs(String absoluteDependencyDirPath, String classToExecute) {
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add(Utils.getJavaPathBuilder(env.get("PATH+JDK"), launcher));
        if (StringUtils.isNotBlank(javaArgs)) {
            args.add(javaArgs.split("\\s+"));
        }
        args.add("-cp", absoluteDependencyDirPath + "/*");
        args.add("org.jfrog.build.extractor.npm.extractor." + classToExecute);
        if (!launcher.isUnix()) {
            return args.toWindowsCommand();
        }
        return args;
    }
}
