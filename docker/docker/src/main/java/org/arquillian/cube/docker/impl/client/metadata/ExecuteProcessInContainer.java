package org.arquillian.cube.docker.impl.client.metadata;

import io.fabric8.docker.api.model.ContainerState;
import org.arquillian.cube.docker.impl.docker.DockerClientExecutor;
import org.arquillian.cube.spi.metadata.CanExecuteProcessInContainer;

public class ExecuteProcessInContainer implements CanExecuteProcessInContainer {

    private String cubeId;
    private DockerClientExecutor executor;

    public ExecuteProcessInContainer(String cubeId, DockerClientExecutor executor) {
        this.cubeId = cubeId;
        this.executor = executor;
    }

    @Override
    public ExecResult exec(String... command) {
        final DockerClientExecutor.ExecInspection execInspection = this.executor.execStartVerbose(cubeId, command);
        final ContainerState containerState = execInspection.getContainerInspect().getState();

        return new ExecResult(execInspection.getOutput(),
            containerState.getRunning(),
            containerState.getExitCode());
    }
}
