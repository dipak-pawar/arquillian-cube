package org.arquillian.cube.docker.impl.model;

import io.fabric8.docker.api.model.ContainerInspect;
import io.fabric8.docker.api.model.HostConfig;
import io.fabric8.docker.client.DockerClient;
import io.fabric8.docker.client.DockerClientException;
import io.fabric8.docker.client.impl.ContainerNamedOperationImpl;
import io.fabric8.docker.client.impl.ContainerOperationImpl;
import io.fabric8.docker.dsl.container.ContainerExecResourceLogsAttachArchiveInterface;
import io.fabric8.docker.dsl.container.ContainerInterface;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.arquillian.cube.docker.impl.client.config.CubeContainer;
import org.arquillian.cube.docker.impl.docker.DockerClientExecutor;
import org.arquillian.cube.spi.event.lifecycle.AfterCreate;
import org.arquillian.cube.spi.event.lifecycle.AfterDestroy;
import org.arquillian.cube.spi.event.lifecycle.AfterStart;
import org.arquillian.cube.spi.event.lifecycle.AfterStop;
import org.arquillian.cube.spi.event.lifecycle.BeforeCreate;
import org.arquillian.cube.spi.event.lifecycle.BeforeDestroy;
import org.arquillian.cube.spi.event.lifecycle.BeforeStart;
import org.arquillian.cube.spi.event.lifecycle.BeforeStop;
import org.jboss.arquillian.core.api.Injector;
import org.jboss.arquillian.core.api.Instance;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.arquillian.core.test.AbstractManagerTestBase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DockerCubeTest extends AbstractManagerTestBase {

    private static final String ID = "test";

    @Mock
    private DockerClientExecutor executor;

    @Mock
    private DockerClient dockerClient;

    @Mock
    private ContainerInterface containerInterface;

    @Mock
    private ContainerExecResourceLogsAttachArchiveInterface containerExecResourceLogsAttachArchiveInterface;

    @Mock
    private ContainerInspect inspectContainerResponse;

    @Inject
    private Instance<Injector> injectorInst;

    private DockerCube cube;

    @Before
    public void setup() {
        HostConfig hostConfig = new HostConfig();
        final Map<String, ArrayList<io.fabric8.docker.api.model.PortBinding>> portBindings = new HashMap<>();
        hostConfig.setPortBindings(portBindings); //new Ports();
        when(inspectContainerResponse.getHostConfig()).thenReturn(hostConfig);
        when(dockerClient.container()).thenReturn(containerInterface);
        when(containerInterface.withName(anyString())).thenReturn(containerExecResourceLogsAttachArchiveInterface);
        //when(inspectContainerCmd.inspect()).thenReturn(inspectContainerResponse);
        when(dockerClient.container().withName(anyString()).inspect()).thenReturn(inspectContainerResponse);
        when(executor.getDockerClient()).thenReturn(dockerClient);
        CubeContainer cubeContainer = new CubeContainer();
        cubeContainer.setRemoveVolumes(false);
        cube = injectorInst.get().inject(new DockerCube(ID, cubeContainer, executor));
    }

    @Test
    public void shouldFireLifecycleEventsDuringCreate() {
        cube.create();
        assertEventFired(BeforeCreate.class, 1);
        assertEventFired(AfterCreate.class, 1);
    }

    @Test
    public void shouldFireLifecycleEventsDuringStart() {
        cube.start();
        assertEventFired(BeforeStart.class, 1);
        assertEventFired(AfterStart.class, 1);
    }

    @Test
    public void shouldFireLifecycleEventsDuringStop() {
        cube.stop();
        assertEventFired(BeforeStop.class, 1);
        assertEventFired(AfterStop.class, 1);
    }

    @Test
    public void shouldFireLifecycleEventsDuringStopWhenContainerNotFound() {
        doThrow(new DockerClientException("Message: Not Found.", 404))
            .when(executor).stopContainer(ID);
        cube.stop();
        assertEventFired(BeforeStop.class, 1);
        assertEventFired(AfterStop.class, 1);
    }

    @Test
    public void shouldFireLifecycleEventsDuringDestroy() {
        cube.stop(); // require a stopped Cube to destroy it.
        cube.destroy();
        assertEventFired(BeforeDestroy.class, 1);
        assertEventFired(AfterDestroy.class, 1);
    }

    @Test
    public void shouldFireLifecycleEventsDuringDestroyWhenContainerNotFound() {
        doThrow(new DockerClientException("Message: Not Found.", 404))
            .when(executor).removeContainer(ID, false);
        cube.stop();
        cube.destroy();
        assertEventFired(BeforeDestroy.class, 1);
        assertEventFired(AfterDestroy.class, 1);
    }
}
