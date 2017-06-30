package org.arquillian.cube.containerobject.dsl;

import io.fabric8.docker.api.model.NetworkResource;
import io.fabric8.docker.client.DockerClient;
import java.util.List;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.DockerNetwork;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.Network;
import org.arquillian.cube.docker.impl.requirement.RequiresDockerMachine;
import org.arquillian.cube.requirement.ArquillianConditionalRunner;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(ArquillianConditionalRunner.class)
//@RequiresDockerMachine(name = "dev")
public class NetworkTest {

    @DockerNetwork
    Network network = Network.withDefaultDriver("mynetwork").build();

    @ArquillianResource
    DockerClient dockerClient;

    // https://github.com/fabric8io/docker-client/issues/88
    @Test @Ignore
    public void should_create_network() {

        List<NetworkResource> mynetwork =
            dockerClient.network().list().filters("name", "mynetwork").all();

        assertThat(mynetwork)
            .hasSize(1)
            .extracting(NetworkResource::getName)
            .contains("mynetwork");
    }
}
