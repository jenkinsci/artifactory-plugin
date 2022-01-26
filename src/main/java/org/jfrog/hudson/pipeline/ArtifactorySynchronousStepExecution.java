package org.jfrog.hudson.pipeline;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;
import org.jfrog.hudson.ArtifactoryServer;
import org.jfrog.hudson.util.JenkinsBuildInfoLog;

import java.io.IOException;

import static org.jfrog.hudson.pipeline.common.Utils.extractRootWorkspace;

public abstract class ArtifactorySynchronousStepExecution<T> extends SynchronousStepExecution<T> {
    protected static final long serialVersionUID = 1L;

    protected transient TaskListener listener;
    protected transient Launcher launcher;
    protected transient WorkflowRun build;
    // The step's root workspace
    protected transient FilePath rootWs;
    // The step's working directory
    protected transient FilePath ws;
    protected transient EnvVars env;

    protected ArtifactorySynchronousStepExecution(StepContext context) throws IOException, InterruptedException {
        super(context);
        this.listener = context.get(TaskListener.class);
        this.build = context.get(WorkflowRun.class);
        this.launcher = context.get(Launcher.class);
        this.ws = context.get(FilePath.class);
        this.rootWs = extractRootWorkspace(context, this.build, this.ws);
        this.env = context.get(EnvVars.class);
    }

    protected abstract T runStep() throws Exception;

    public abstract org.jfrog.hudson.ArtifactoryServer getUsageReportServer() throws Exception;

    public abstract String getUsageReportFeatureName();

    @Override
    protected T run() throws Exception {
        try {
            ArtifactoryServer server = getUsageReportServer();
            if (server != null) {
                new Thread(() -> {
                    server.reportUsage(getUsageReportFeatureName(), build, new JenkinsBuildInfoLog(listener));
                }).start();
            }
            return runStep();
        } finally {
            listener.getLogger().flush();
        }
    }
}
