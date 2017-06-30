package org.arquillian.cube.docker.impl.util;

import io.fabric8.docker.api.model.Config;
import io.fabric8.docker.api.model.ContainerInspect;
import io.fabric8.docker.api.model.HostConfig;
import io.fabric8.docker.api.model.NetworkSettings;
import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;
import org.arquillian.cube.docker.impl.client.config.CubeContainer;
import org.arquillian.cube.docker.impl.client.config.PortBinding;
import org.arquillian.cube.docker.impl.docker.DockerClientExecutor;
import org.arquillian.cube.spi.Binding;

public final class BindingUtil {

    public static final String PORTS_SEPARATOR = "->";

    private BindingUtil() {
    }

    public static Binding binding(DockerClientExecutor executor, String cubeId) {
        ContainerInspect inspectResponse = executor.getDockerClient().container().withName(cubeId).inspect();


        String dockerIp = getDockerServerIp(executor);
        String inernalIp = null;
        NetworkSettings networkSettings = inspectResponse.getNetworkSettings();
        if (networkSettings != null) {
            inernalIp = networkSettings.getIPAddress();
        }

        Binding binding = new Binding(dockerIp, inernalIp);

        HostConfig hostConfig = inspectResponse.getHostConfig();

        if (hostConfig.getPortBindings() != null) {

            for (Entry<String, ArrayList<io.fabric8.docker.api.model.PortBinding>> bind : hostConfig.getPortBindings().entrySet()) {
                final ArrayList<io.fabric8.docker.api.model.PortBinding> allBindings = bind.getValue();
                for (io.fabric8.docker.api.model.PortBinding bindings : allBindings) {
                    binding.addPortBinding(getPort(bind.getKey()), Integer.parseInt(bindings.getHostPort()));
                }
            }
        } else {
            final Config config = inspectResponse.getConfig();
            final Map<String, Object> exposedPorts = config.getExposedPorts();
            if (exposedPorts != null && !exposedPorts.isEmpty()) {

                for (String port : exposedPorts.keySet()) {
                    binding.addPortBinding(getPort(port), -1);
                }
            }
        }
        return binding;
    }

    private static int getPort(String protocolWithPort) {
        final String port = protocolWithPort.split("/")[0];

        return Integer.valueOf(port);
    }

    private static String getDockerServerIp(DockerClientExecutor executor) {
        return executor.getDockerServerIp();
    }

    public static Binding binding(CubeContainer cubeConfiguration, DockerClientExecutor executor) {

        Binding binding = new Binding(executor.getDockerServerIp());

        if (cubeConfiguration.getPortBindings() != null) {
            for (PortBinding cubePortBinding : cubeConfiguration.getPortBindings()) {
                binding.addPortBinding(cubePortBinding.getExposedPort().getExposed(), cubePortBinding.getBound());
            }
        }
        return binding;
    }
}
