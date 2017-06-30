package org.arquillian.cube.docker.impl.requirement;

import io.fabric8.docker.api.model.Version;
import io.fabric8.docker.client.Config;
import io.fabric8.docker.client.ConfigBuilder;
import io.fabric8.docker.client.DefaultDockerClient;
import io.fabric8.docker.client.DockerClient;
import io.fabric8.docker.client.EditableConfig;
import java.util.HashMap;
import java.util.Map;
import org.arquillian.cube.docker.impl.client.CubeDockerConfiguration;
import org.arquillian.cube.docker.impl.client.CubeDockerConfigurationResolver;
import org.arquillian.cube.docker.impl.util.Boot2Docker;
import org.arquillian.cube.docker.impl.util.CommandLineExecutor;
import org.arquillian.cube.docker.impl.util.DockerMachine;
import org.arquillian.cube.docker.impl.util.OperatingSystemResolver;
import org.arquillian.cube.docker.impl.util.Top;
import org.arquillian.cube.impl.util.Strings;
import org.arquillian.cube.spi.requirement.Requirement;
import org.arquillian.cube.spi.requirement.UnsatisfiedRequirementException;
import org.arquillian.spacelift.execution.ExecutionException;

import static io.fabric8.docker.client.utils.Utils.isNullOrEmpty;

public class DockerRequirement implements Requirement<RequiresDocker> {

    private final CommandLineExecutor commandLineExecutor;
    private final CubeDockerConfigurationResolver resolver;

    public DockerRequirement() {
        this.commandLineExecutor = new CommandLineExecutor();
        this.resolver = new CubeDockerConfigurationResolver(new Top(),
            new DockerMachine(commandLineExecutor),
            new Boot2Docker(commandLineExecutor),
            new OperatingSystemResolver().currentOperatingSystem().getFamily()
        );
    }

    public DockerRequirement(CommandLineExecutor commandLineExecutor, CubeDockerConfigurationResolver resolver) {
        this.commandLineExecutor = commandLineExecutor;
        this.resolver = resolver;
    }

    /**
     * @param serverUrl
     *     The url to check if docker is running on.
     *
     * @return True if docker is running on the url.
     */
    private static boolean isDockerRunning(String serverUrl) {
        return getDockerVersion(serverUrl) != null;
    }

    /**
     * Returns the docker version.
     *
     * @param serverUrl
     *     The serverUrl to use.
     */
    private static Version getDockerVersion(String serverUrl) {
        try {
            final Config build = new ConfigBuilder().withDockerUrl(serverUrl).build();
            final DockerClient dockerClient = new DefaultDockerClient(build);

            return dockerClient.version();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void check(RequiresDocker context) throws UnsatisfiedRequirementException {
        try {
            Map<String, String> config = resolver.resolve(new HashMap<String, String>());
            String serverUrl = config.get(CubeDockerConfiguration.DOCKER_URI);
            if (Strings.isNullOrEmpty(serverUrl)) {
                throw new UnsatisfiedRequirementException("Could not resolve the docker server url.");
            } else if (!isDockerRunning(serverUrl)) {
                throw new UnsatisfiedRequirementException("No server is running on url:[" + serverUrl + "].");
            }
        } catch (ExecutionException e) {
            throw new UnsatisfiedRequirementException("Cannot execute docker command.");
        }
    }
}
