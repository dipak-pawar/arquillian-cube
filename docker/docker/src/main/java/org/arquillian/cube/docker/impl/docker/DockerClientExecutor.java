package org.arquillian.cube.docker.impl.docker;

import io.fabric8.docker.api.model.Container;
import io.fabric8.docker.api.model.ContainerChange;
import io.fabric8.docker.api.model.ContainerCreateRequestBuilder;
import io.fabric8.docker.api.model.ContainerCreateRequestFluent;
import io.fabric8.docker.api.model.ContainerExecCreateResponse;
import io.fabric8.docker.api.model.ContainerInspect;
import io.fabric8.docker.api.model.ContainerProcessList;
import io.fabric8.docker.api.model.DeviceMapping;
import io.fabric8.docker.api.model.EditableContainerCreateRequest;
import io.fabric8.docker.api.model.IPAM;
import io.fabric8.docker.api.model.InlineNetworkCreate;
import io.fabric8.docker.api.model.NetworkCreateResponse;
import io.fabric8.docker.api.model.NetworkResource;
import io.fabric8.docker.api.model.PortBinding;
import io.fabric8.docker.api.model.PortBindingBuilder;
import io.fabric8.docker.api.model.RestartPolicy;
import io.fabric8.docker.api.model.Stats;
import io.fabric8.docker.api.model.Version;
import io.fabric8.docker.client.Config;
import io.fabric8.docker.client.ConfigBuilder;
import io.fabric8.docker.client.DefaultDockerClient;
import io.fabric8.docker.client.DockerClient;
import io.fabric8.docker.client.DockerClientException;
import io.fabric8.docker.dsl.EventListener;
import io.fabric8.docker.dsl.OutputHandle;
import io.fabric8.docker.dsl.container.SinceContainerOutputErrorTimestampsTailingLinesUsingListenerFollowDisplayInterface;
import io.fabric8.docker.dsl.image.RedirectingWritingOutputFromPathInterface;
import io.fabric8.docker.dsl.image.RepositoryNameSupressingVerboseOutputNoCachePullingRemoveIntermediateMemorySwapCpuSharesCpusPeriodQuotaBuildArgsUsingDockerFileListenerRedirectingWritingFromPathInterface;
import io.fabric8.docker.dsl.image.UsingListenerRedirectingWritingOutputTagFromRegistryInterface;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.arquillian.cube.TopContainer;
import org.arquillian.cube.docker.impl.client.CubeDockerConfiguration;
import org.arquillian.cube.docker.impl.client.config.BuildImage;
import org.arquillian.cube.docker.impl.client.config.CubeContainer;
import org.arquillian.cube.docker.impl.client.config.ExposedPort;
import org.arquillian.cube.docker.impl.client.config.IPAMConfig;
import org.arquillian.cube.docker.impl.client.config.Image;
import org.arquillian.cube.docker.impl.client.config.Link;
import org.arquillian.cube.docker.impl.client.config.Network;
import org.arquillian.cube.docker.impl.util.BindingUtil;
import org.arquillian.cube.spi.CubeOutput;
import org.omg.CORBA.TIMEOUT;

import static org.arquillian.cube.docker.impl.client.config.ExposedPort.getExposedPorts;

public class DockerClientExecutor {

    public static final String PATH_IN_CONTAINER = "pathInContainer";
    public static final String PATH_ON_HOST = "pathOnHost";
    public static final String C_GROUP_PERMISSIONS = "cGroupPermissions";
    public static final String PORTS_SEPARATOR = BindingUtil.PORTS_SEPARATOR;
    public static final String TAG_SEPARATOR = ":";
    public static final String RESTART_POLICY = "restartPolicy";
    public static final String CAP_DROP = "capDrop";
    public static final String CAP_ADD = "capAdd";
    public static final String DEVICES = "devices";
    public static final String DNS_SEARCH = "dnsSearch";
    public static final String NETWORK_MODE = "networkMode";
    public static final String PUBLISH_ALL_PORTS = "publishAllPorts";
    public static final String PRIVILEGED = "privileged";
    public static final String PORT_BINDINGS = "portBindings";
    public static final String LINKS = "links";
    public static final String BINDS = "binds";
    public static final String VOLUMES_FROM = "volumesFrom";
    public static final String VOLUMES = "volumes";
    public static final String DNS = "dns";
    public static final String CMD = "cmd";
    public static final String ENV = "env";
    public static final String EXPOSED_PORTS = "exposedPorts";
    public static final String ATTACH_STDERR = "attachStderr";
    public static final String ATTACH_STDIN = "attachStdin";
    public static final String CPU_SHARES = "cpuShares";
    public static final String MEMORY_SWAP = "memorySwap";
    public static final String MEMORY_LIMIT = "memoryLimit";
    public static final String STDIN_ONCE = "stdinOnce";
    public static final String STDIN_OPEN = "stdinOpen";
    public static final String TTY = "tty";
    public static final String USER = "user";
    public static final String PORT_SPECS = "portSpecs";
    public static final String HOST_NAME = "hostName";
    public static final String DISABLE_NETWORK = "disableNetwork";
    public static final String WORKING_DIR = "workingDir";
    public static final String IMAGE = "image";
    public static final String BUILD_IMAGE = "buildImage";
    public static final String DOCKERFILE_LOCATION = "dockerfileLocation";
    public static final String NO_CACHE = "noCache";
    public static final String REMOVE = "remove";
    public static final String ALWAYS_PULL = "alwaysPull";
    public static final String ENTRYPOINT = "entryPoint";
    public static final String CPU_SET = "cpuSet";
    public static final String DOCKERFILE_NAME = "dockerfileName";
    public static final String EXTRA_HOSTS = "extraHosts";
    public static final String READ_ONLY_ROOT_FS = "ReadonlyRootfs";
    public static final String LABELS = "labels";
    public static final String DOMAINNAME = "domainName";
    private static final String DEFAULT_C_GROUPS_PERMISSION = "rwm";
    private static final Logger log = Logger.getLogger(DockerClientExecutor.class.getName());
    private static final Pattern IMAGEID_PATTERN = Pattern.compile(".*Successfully built\\s(\\p{XDigit}+)");
    private final URI dockerUri;
    private final String dockerServerIp;
    private DockerClient dockerClient;
    private CubeDockerConfiguration cubeConfiguration;
    private Config dockerClientConfig;

    public DockerClientExecutor(CubeDockerConfiguration cubeConfiguration) {

        final ConfigBuilder configBuilder = new ConfigBuilder();
        //final DefaultDockerClientConfig.Builder configBuilder = DefaultDockerClientConfig
        //    .createDefaultConfigBuilder();

        String dockerServerUri = cubeConfiguration.getDockerServerUri();

        dockerUri = URI.create(dockerServerUri);
        dockerServerIp = cubeConfiguration.getDockerServerIp();

        configBuilder.withDockerUrl(dockerUri.toString());

        //configBuilder.withApiVersion(cubeConfiguration.getDockerServerVersion())
        //    .withDockerHost(dockerUri.toString());

        if (cubeConfiguration.getUsername() != null) {
            configBuilder.withUsername(cubeConfiguration.getUsername());
        }

        if (cubeConfiguration.getPassword() != null) {
            configBuilder.withPassword(cubeConfiguration.getPassword());
        }

        this.dockerClientConfig = configBuilder.build();

        this.cubeConfiguration = cubeConfiguration;
        this.dockerClient = buildDockerClient();
    }

    public static String getImageId(String fullLog) {
        Matcher m = IMAGEID_PATTERN.matcher(fullLog);
        String imageId = null;
        if (m.find()) {
            imageId = m.group(1);
        }
        return imageId;
    }

    private static final DeviceMapping[] toDevices(
        Collection<org.arquillian.cube.docker.impl.client.config.Device> deviceList) {

        DeviceMapping[] devices = new DeviceMapping[deviceList.size()];

        int i = 0;
        for (org.arquillian.cube.docker.impl.client.config.Device device : deviceList) {
            if (device.getPathOnHost() != null
                && device.getPathInContainer() != null) {

                String cGroupPermissions;
                if (device.getcGroupPermissions() != null) {
                    cGroupPermissions = device.getcGroupPermissions();
                } else {
                    cGroupPermissions = DEFAULT_C_GROUPS_PERMISSION;
                }

                String pathOnHost = device.getPathOnHost();
                String pathInContainer = device.getPathInContainer();

                devices[i] = new DeviceMapping(cGroupPermissions, pathInContainer, pathOnHost);
                i++;
            }
        }

        return devices;
    }

    private static final RestartPolicy toRestartPolicy(
        org.arquillian.cube.docker.impl.client.config.RestartPolicy restart) {
        if (restart.getName() != null) {
            String name = restart.getName();

            if ("failure".equals(name)) {
                return new RestartPolicy(restart.getMaximumRetryCount(), "on-failure");
            } else {
                if ("restart".equals(name)) {
                    return new RestartPolicy(0, "always");
                } else {
                    return new RestartPolicy();
                }
            }
        } else {
            return new RestartPolicy();
        }
    }

    private static final Link[] toLinks(Collection<org.arquillian.cube.docker.impl.client.config.Link> linkList) {
        Link[] links = new Link[linkList.size()];
        int i = 0;
        for (org.arquillian.cube.docker.impl.client.config.Link link : linkList) {
            links[i] = new Link(link.getName(), link.getAlias());
            i++;
        }

        return links;
    }

    private static final Map<String, Object> toVolumes(Collection<String> volumesList) {
        Map<String, Object> map = new HashMap<>();
        for (String volume : volumesList) {
            String[] volumeSection = volume.split(":");

            if (volumeSection.length == 2 || volumeSection.length == 3) {
                map.put(volumeSection[1], new HashMap<>());
            } else {
                map.put(volumeSection[0], new HashMap<>());
            }
        }

        return map;
    }

   /* private static final VolumesFrom[] toVolumesFrom(Collection<String> volumesFromList) {

        VolumesFrom[] volumesFrom = new VolumesFrom[volumesFromList.size()];

        int i = 0;
        for (String volumesFromm : volumesFromList) {
            volumesFrom[i] = VolumesFrom.parse(volumesFromm);
            i++;
        }
        return volumesFrom;
    }*/

    public DockerClient buildDockerClient() {
        return new DefaultDockerClient(dockerClientConfig);
    }

    public List<Container> listRunningContainers() {
        return this.dockerClient.container().list().running();
    }

    public String createContainer(String name, CubeContainer containerConfiguration) {
        String image = getImageName(containerConfiguration, name);

        final ContainerCreateRequestBuilder containerCreateRequestBuilder = new ContainerCreateRequestBuilder();
        containerCreateRequestBuilder.withImage(image)
            .withName(name);

        Set<ExposedPort> allExposedPorts = resolveExposedPorts(containerConfiguration);
        if (!allExposedPorts.isEmpty()) {
            containerCreateRequestBuilder.withExposedPorts(getExposedPorts(allExposedPorts));
        }

        if (containerConfiguration.getLabels() != null) {
            containerCreateRequestBuilder.withLabels(containerConfiguration.getLabels());
        }

        if (containerConfiguration.getWorkingDir() != null) {
            containerCreateRequestBuilder.withWorkingDir(containerConfiguration.getWorkingDir());
        }

        if (containerConfiguration.getDisableNetwork() != null) {
            containerCreateRequestBuilder.withNetworkDisabled(containerConfiguration.getDisableNetwork());
        }

        if (containerConfiguration.getHostName() != null) {
            containerCreateRequestBuilder.withHostname(containerConfiguration.getHostName());
        }
        if (containerConfiguration.getUser() != null) {
            containerCreateRequestBuilder.withUser(containerConfiguration.getUser());
        }

        if (containerConfiguration.getTty() != null) {
            containerCreateRequestBuilder.withTty(containerConfiguration.getTty());
        }

        if (containerConfiguration.getStdinOpen() != null) {
            containerCreateRequestBuilder.withOpenStdin(containerConfiguration.getStdinOpen());
        }

        if (containerConfiguration.getStdinOnce() != null) {
            containerCreateRequestBuilder.withStdinOnce(containerConfiguration.getStdinOnce());
        }

        if (containerConfiguration.getMemoryLimit() != null) {
            containerCreateRequestBuilder.withMemory(String.valueOf(containerConfiguration.getMemoryLimit()));
        }

        if (containerConfiguration.getMemorySwap() != null) {
            containerCreateRequestBuilder.withMemorySwap(String.valueOf(containerConfiguration.getMemorySwap()));
        }

        if (containerConfiguration.getCpuShares() != null) {
            containerCreateRequestBuilder.withCpuShares(String.valueOf(containerConfiguration.getCpuShares()));
        }

        if (containerConfiguration.getCpuSet() != null) {
            containerCreateRequestBuilder.withCpusetCpus(containerConfiguration.getCpuSet());
        }

        if (containerConfiguration.getCpuQuota() != null) {
            containerCreateRequestBuilder.getHostConfig().setCpuQuota(
                Long.valueOf(containerConfiguration.getCpuQuota()));
        }

        if (containerConfiguration.getAttachStdin() != null) {
            containerCreateRequestBuilder.withAttachStdin(containerConfiguration.getAttachStdin());
        }

        if (containerConfiguration.getAttachSterr() != null) {
            containerCreateRequestBuilder.withAttachStderr(containerConfiguration.getAttachSterr());
        }

        if (containerConfiguration.getEnv() != null) {
            containerCreateRequestBuilder.withEnv(
                resolveDockerServerIpInList(containerConfiguration.getEnv()));
        }

        if (containerConfiguration.getCmd() != null) {
            containerCreateRequestBuilder.withCmd(containerConfiguration.getCmd().toArray(new String[0]));
        }

        if (containerConfiguration.getVolumes() != null) {
            containerCreateRequestBuilder.withVolumes(toVolumes(containerConfiguration.getVolumes()));
        }

        if (containerConfiguration.getEntryPoint() != null) {
            containerCreateRequestBuilder.withEntrypoint(
                containerConfiguration.getEntryPoint().iterator().next());
        }

        if (containerConfiguration.getDomainName() != null) {
            containerCreateRequestBuilder.withDomainname(containerConfiguration.getDomainName());
        }

        if (containerConfiguration.getIpv4Address() != null) {
            containerCreateRequestBuilder.withIpv4Address(containerConfiguration.getIpv4Address());
        }

        if (containerConfiguration.getIpv6Address() != null) {
            containerCreateRequestBuilder.withIpv6Address(containerConfiguration.getIpv6Address());
        }

        final ContainerCreateRequestFluent.HostConfigNested<ContainerCreateRequestBuilder>
            hostConfig = containerCreateRequestBuilder.withNewHostConfig();

        if (containerConfiguration.getReadonlyRootfs() != null) {
            hostConfig.withReadonlyRootfs(containerConfiguration.getReadonlyRootfs());
        }

        if (containerConfiguration.getPrivileged() != null) {
            hostConfig.withPrivileged(containerConfiguration.getPrivileged());
        }

        if (containerConfiguration.getPublishAllPorts() != null) {
            hostConfig.withPublishAllPorts(containerConfiguration.getPublishAllPorts());
        }

        if (containerConfiguration.getNetworkMode() != null) {
            hostConfig.withNetworkMode(containerConfiguration.getNetworkMode());
        }

        /* TODO: 6/19/17 - it's available in 1.22 onwards. This is not supported in API 1.21.
        if (containerConfiguration.getShmSize() != null) {
            hostConfig.withShmSize(containerConfiguration.getShmSize());
        }*/

        final Collection<String> volumesFrom = containerConfiguration.getVolumesFrom();
        if (volumesFrom != null) {
            hostConfig.withVolumesFrom(volumesFrom.toArray(new String[volumesFrom.size()]));
        }

        final Collection<String> binds = containerConfiguration.getBinds();
        if (binds != null) {
            hostConfig.withBinds(binds.toArray(new String[binds.size()]));
        }

        // Dependencies is precedence over links
        final Collection<Link> links = containerConfiguration.getLinks();
        if (links != null && containerConfiguration.getDependsOn() == null) {
            hostConfig.withLinks(links.stream().map(Link::toString).toArray(String[]::new));
        }

        if (containerConfiguration.getPortBindings() != null) {
            hostConfig.withPortBindings(toPortBindings(containerConfiguration.getPortBindings()));
        }

        if (containerConfiguration.getDnsSearch() != null) {
            hostConfig.withDnsSearch(containerConfiguration.getDnsSearch().toArray(new String[0]));
        }

        if (containerConfiguration.getDevices() != null) {
            hostConfig.withDevices(toDevices(containerConfiguration.getDevices()));
        }

        if (containerConfiguration.getRestartPolicy() != null) {
            hostConfig.withRestartPolicy(toRestartPolicy(containerConfiguration.getRestartPolicy()));
        }

        final Collection<String> capAdd = containerConfiguration.getCapAdd();
        if (capAdd != null) {
            hostConfig.withCapAdd(capAdd.toArray(new String[capAdd.size()]));
        }

        final Collection<String> capDrop = containerConfiguration.getCapDrop();
        if (capDrop != null) {
            hostConfig.withCapDrop(capDrop.toArray(new String[capDrop.size()]));
        }

        if (containerConfiguration.getExtraHosts() != null) {
            hostConfig.withExtraHosts(containerConfiguration.getExtraHosts().toArray(new String[0]));
        }

        if (containerConfiguration.getDns() != null) {
            hostConfig.withDns(containerConfiguration.getDns().toArray(new String[0]));
        }

        hostConfig.endHostConfig();

        boolean alwaysPull = false;

        if (containerConfiguration.getAlwaysPull() != null) {
            alwaysPull = containerConfiguration.getAlwaysPull();
        }

        if (alwaysPull) {
            log.info(String.format(
                "Pulling latest Docker Image %s.", image));
            this.pullImage(image);
        }

        final EditableContainerCreateRequest build = containerCreateRequestBuilder.build();
        try {
            return this.dockerClient.container().create(build).getId();
        } catch (DockerClientException e) {
            if (e.getMessage().contains("Message: Not Found.") && 404 == e.getCode()) {
                if (!alwaysPull) {
                    log.warning(String.format(
                        "Docker Image %s is not on DockerHost and it is going to be automatically pulled.", image));
                    this.pullImage(image);
                    return this.dockerClient.container().create(build).getId();
                } else {
                    throw e;
                }
            } else if (e.getMessage().contains("Message: Conflict.") && 409 == e.getCode()) {
                if (cubeConfiguration.isClean()) {
                    log.warning(String.format("Container name %s is already use. Since clean mode is enabled, " +
                        "container is going to be self removed.", name));
                    try {
                        this.stopContainer(name);
                    } catch (DockerClientException e1) {
                        if (e1.getMessage().contains("Not Modified.")) {
                            // Container was already stopped
                        } else {
                            throw e1;
                        }
                    }
                    this.removeContainer(name, containerConfiguration.getRemoveVolumes());
                    return this.dockerClient.container().create(build).getId();
                } else {
                    throw e;
                }
            } else {
                throw e;
            }
                /*catch (ProcessingException e) {
                if (e.getCause() instanceof UnsupportedSchemeException) {
                    if (e.getCause().getMessage().contains("https")) {
                        throw new IllegalStateException("You have configured serverUri with https protocol but " +
                            "certPath property is missing or points out to an invalid certificate to handle the SSL.",
                            e.getCause());
                    } else {
                        throw e;
                    }
                } else {
                    throw e;
                }
            }*/
        }
    }

    private Map<String, String> resolveDockerServerIpInList(Collection<String> envs) {
        Map<String, String> resolvedEnv = new HashMap<>();
        for (String env : envs) {
            if (env.contains(CubeDockerConfiguration.DOCKER_SERVER_IP)) {
                final String replaceAll =
                    env.replaceAll(CubeDockerConfiguration.DOCKER_SERVER_IP, cubeConfiguration.getDockerServerIp());
                final String[] split = replaceAll.split("=");
                resolvedEnv.put(split[0], split[1]);
            } else {
                final String[] split = env.split("=", 2);
                resolvedEnv.put(split[0], split[1]);
            }
        }
        return resolvedEnv;
    }

    private Set<ExposedPort> resolveExposedPorts(CubeContainer containerConfiguration) {
        Set<ExposedPort> allExposedPorts = new HashSet<>();
        if (containerConfiguration.getPortBindings() != null) {
            for (org.arquillian.cube.docker.impl.client.config.PortBinding binding : containerConfiguration.getPortBindings()) {
                allExposedPorts.add(
                    new ExposedPort(binding.getExposedPort().getExposed(), binding.getExposedPort().getType()));
            }
        }
        if (containerConfiguration.getExposedPorts() != null) {
            for (org.arquillian.cube.docker.impl.client.config.ExposedPort port : containerConfiguration.getExposedPorts()) {
                allExposedPorts.add(new ExposedPort(port.getExposed(), port.getType()));
            }
        }
        return allExposedPorts;
    }

    private String getImageName(CubeContainer containerConfiguration, String name) {
        String image;

        if (containerConfiguration.getImage() != null) {
            image = containerConfiguration.getImage().toImageRef();
        } else {

            if (containerConfiguration.getBuildImage() != null) {

                BuildImage buildImage = containerConfiguration.getBuildImage();

                if (buildImage.getDockerfileLocation() != null) {
                    Map<String, Object> params = new HashMap<>(); //(containerConfiguration, BUILD_IMAGE);
                    params.put("noCache", buildImage.isNoCache());
                    params.put("remove", buildImage.isRemove());
                    params.put("dockerFileLocation", buildImage.getDockerfileLocation());
                    params.put("dockerFileName", buildImage.getDockerfileName());

                    image = this.buildImage(buildImage.getDockerfileLocation(), name, params);
                } else {
                    throw new IllegalArgumentException(
                        "A tar file with Dockerfile on root or a directory with a Dockerfile should be provided.");
                }
            } else {
                throw new IllegalArgumentException(
                    String.format(
                        "Current configuration file does not contain %s nor %s parameter and one of both should be provided.",
                        IMAGE, BUILD_IMAGE));
            }
        }
        return image;
    }

    public void startContainer(String id, CubeContainer containerConfiguration) {
        this.dockerClient.container().withName(id).start();
    }

    public Stats statsContainer(String id) throws IOException {
        return this.dockerClient.container().withName(id).stats();
    }

    private Map<String, ArrayList<PortBinding>> toPortBindings(
        Collection<org.arquillian.cube.docker.impl.client.config.PortBinding> portBindings) {
        Map<String, ArrayList<PortBinding>> map = new HashMap<>();

        for (org.arquillian.cube.docker.impl.client.config.PortBinding portBinding : portBindings) {
            map.put(
                String.valueOf(portBinding.getExposedPort().getExposed() + "/" + portBinding.getExposedPort().getType()),
                new ArrayList<>(Collections.singletonList(new PortBindingBuilder()
                    .withHostIp(portBinding.getHost())
                    .withHostPort(String.valueOf(portBinding.getBound())).build())));
        }

        return map;
    }

    public void killContainer(String containerId) {
        this.dockerClient.container().withName(containerId).kill();
    }

    public void stopContainer(String containerId) {
        this.dockerClient.container().withName(containerId).stop();
    }

    public void removeContainer(String containerId, boolean removeVolumes) {
        this.dockerClient.container().withName(containerId).remove(removeVolumes);
    }

    public ContainerInspect inspectContainer(String containerId) {
        return this.dockerClient.container().withName(containerId).inspect();
    }

    public int waitContainer(String containerId) {
        return this.dockerClient.container().withName(containerId).waitContainer();
    }

    public Version dockerHostVersion() {
        return this.dockerClient.version();
    }

    public void pingDockerServer() {
        if (!this.dockerClient.ping()) {
            throw new IllegalStateException(
                String.format(
                    "Docker server is not running in %s host or it does not accept connections in tcp protocol, read https://github.com/arquillian/arquillian-cube#preliminaries to learn how to enable it.",
                    this.cubeConfiguration.getDockerServerUri()));
        }
    }

    private String buildImage(String location, String name, Map<String, Object> params) {

        final RepositoryNameSupressingVerboseOutputNoCachePullingRemoveIntermediateMemorySwapCpuSharesCpusPeriodQuotaBuildArgsUsingDockerFileListenerRedirectingWritingFromPathInterface<OutputHandle>
            buildImageCommand = this.dockerClient.image().build();
        configureBuildCommand(params, buildImageCommand);

        final CountDownLatch buildDone = new CountDownLatch(1);

        final String[] imageId = new String[1];
        final RedirectingWritingOutputFromPathInterface<OutputHandle>
            outputHandleRedirectingWritingOutputFromPathInterface = buildImageCommand.withRepositoryName(name)
            .usingListener(new EventListener() {
                @Override
                public void onSuccess(String s) {
                    imageId[0] = getImageId(s);
                    buildDone.countDown();
                }

                @Override
                public void onError(String s) {
                    log.severe(s);
                    buildDone.countDown();
                }

                @Override
                public void onEvent(String event) {
                }
            });

        final OutputHandle outputHandle =
            buildFromLocation(location, outputHandleRedirectingWritingOutputFromPathInterface);

        try {
            buildDone.await();
        } catch (InterruptedException e) {
            throw DockerClientException.launderThrowable(e);
        } finally {
            Thread.currentThread().interrupt();
        }

        try {
            outputHandle.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            Thread.currentThread().interrupt();
        }

        if (imageId[0] == null) {
            throw new IllegalStateException(
                String.format(
                    "Docker server has not provided an imageId for image build from %s.",
                    location));
        }

        return imageId[0];
    }

    public void removeImage(String imageName, Boolean force) {
        this.dockerClient.image().withName(imageName).delete().force(force).withNoPrune();
    }

    private void configureBuildCommand(Map<String, Object> params,
        RepositoryNameSupressingVerboseOutputNoCachePullingRemoveIntermediateMemorySwapCpuSharesCpusPeriodQuotaBuildArgsUsingDockerFileListenerRedirectingWritingFromPathInterface buildImageCmd) {
        if (params.containsKey(NO_CACHE) && (boolean) params.get(NO_CACHE)) {
            buildImageCmd.withNoCache();
        }

        if (params.containsKey(REMOVE) && (boolean) params.get(REMOVE)) {
            buildImageCmd.removingIntermediateOnSuccess();
        }

        if (params.containsKey(DOCKERFILE_NAME)) {
            buildImageCmd.usingDockerFile((String) params.get(DOCKERFILE_NAME));
        }
    }

    private OutputHandle buildFromLocation(
        String location, RedirectingWritingOutputFromPathInterface<OutputHandle> buildCommand) {
        try {
            URL url = new URL(location);
            return buildCommand.fromTar(url.openStream());
        } catch (MalformedURLException e) {
            // Means that it is not a URL so it can be a File or Directory
            File file = new File(location);
            if (file.exists()) {
                if (file.isDirectory()) {
                    return buildCommand.fromFolder(location);
                } else {
                    try {
                        return buildCommand.fromTar(new FileInputStream(file));
                    } catch (FileNotFoundException notFoundFile) {
                        throw new IllegalArgumentException(notFoundFile);
                    }
                }
            } else {
                throw new IllegalArgumentException("File [" + file + "] doesn't exists");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String containerLog(String containerId) {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        dockerClient.container().withName(containerId)
            .logs().writingOutput(byteArrayOutputStream)
            .display();

        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            Thread.currentThread().interrupt();
        }

        return byteArrayOutputStream.toString();
    }

    //public static class LogContainerTestCallback extends LogContainerResultCallback {
    //    protected final StringBuilder log = new StringBuilder();
    //
    //    List<Frame> collectedFrames = new ArrayList<>();
    //
    //    boolean collectFrames = false;
    //
    //    public LogContainerTestCallback() {
    //        this(false);
    //    }
    //
    //    public LogContainerTestCallback(boolean collectFrames) {
    //        this.collectFrames = collectFrames;
    //    }
    //
    //    @Override
    //    public void onNext(Frame frame) {
    //        if (collectFrames) collectedFrames.add(frame);
    //        log.append(new String(frame.getPayload()));
    //    }
    //
    //    @Override
    //    public String toString() {
    //        return log.toString();
    //    }
    //
    //    public List<Frame> getCollectedFrames() {
    //        return collectedFrames;
    //    }
    //}

    public void pullImage(String imageName) {
        final Image image = Image.valueOf(imageName);

        final UsingListenerRedirectingWritingOutputTagFromRegistryInterface<OutputHandle> pullImageCmd =
            this.dockerClient.image().withName(image.getName()).pull();

        String tag = image.getTag();
        if (tag != null && !"".equals(tag)) {
            pullImageCmd.withTag(tag);
        } else {
            pullImageCmd.withTag("latest");
        }
        final OutputHandle outputHandle = pullImageCmd.usingListener(new EventListener() {
            @Override
            public void onSuccess(String s) {
                log.info("success: " + s);
            }

            @Override
            public void onError(String s) {
                log.severe(s);
            }

            @Override
            public void onEvent(String s) {
            }
        }).fromRegistry();

        try {
            outputHandle.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public CubeOutput execStart(String containerId, String... commands) {
        String id = execCreate(containerId, commands);
        return execStartOutput(id);
    }

    public void execStartDetached(String containerId, String... commands) {
        String id = execCreate(containerId, commands);
        this.dockerClient.container().withName(id).execNew().withCmd(commands)
            .withDetach(true).done();
    }

    /**
     * EXecutes command to given container returning the inspection object as well. This method does 3 calls to
     * dockerhost. Create, Start and Inspect.
     *
     * @param containerId
     *     to execute command.
     */
    public ExecInspection execStartVerbose(String containerId, String... commands) {
        String id = execCreate(containerId, commands);
        CubeOutput output = execStartOutput(id);

        return new ExecInspection(output, inspectExec(id));
    }

    private ContainerInspect inspectExec(String id) {
        return this.dockerClient.exec().withName(id).inspect();
    }

    private String execCreate(String containerId, String... commands) {
        final ContainerExecCreateResponse containerExecCreateResponse =
            this.dockerClient.container().withName(containerId)
                .execNew().withCmd(commands)
                .withAttachStdout(true).withAttachStderr(true).withTty(false).done();

        return containerExecCreateResponse.getId();
    }

    private CubeOutput execStartOutput(String id) {
        OutputStream outputStream = new ByteArrayOutputStream();
        OutputStream errorStream = new ByteArrayOutputStream();
        try {
            dockerClient.exec().withName(id).writingOutput(outputStream).writingError(errorStream)
                .usingListener(new EventListener() {
                    @Override
                    public void onSuccess(String s) {
                    }

                    @Override
                    public void onError(String s) {
                    }

                    @Override
                    public void onEvent(String s) {
                    }
                }).start(false);
            Thread.sleep(5000);
        } catch (DockerClientException e) {
            return new CubeOutput("", "");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        return new CubeOutput(outputStream.toString(), errorStream.toString());
    }

    public List<org.arquillian.cube.ChangeLog> inspectChangesOnContainerFilesystem(String containerId) {
        final List<ContainerChange> changeLogs = dockerClient.container().withName(containerId).changes();

        List<org.arquillian.cube.ChangeLog> changes = new ArrayList<>();
        for (ContainerChange changeLog : changeLogs) {
            changes.add(new org.arquillian.cube.ChangeLog(changeLog.getPath(), changeLog.getKind()));
        }

        return changes;
    }

    public TopContainer top(String containerId) {
        final ContainerProcessList topContainer = dockerClient.container().withName(containerId).top();
        final List<String> titles = topContainer.getTitles();
        final List<List<String>> processes = topContainer.getProcesses();

        String[][] processesArray = new String[processes.size()][];

        int i = 0;
        for (List<String> nestedList : processes) {
            processesArray[i++] = nestedList.toArray(new String[nestedList.size()]);
        }

        return new TopContainer(titles.toArray(new String[titles.size()]), processesArray);
    }

    public InputStream getFileOrDirectoryFromContainerAsTar(String containerId, String from) {
        return dockerClient.container().withName(containerId).archive().downloadFrom(from);
    }

    public void copyStreamToContainer(String containerId, File from) {
        dockerClient.container().withName(containerId).archive().uploadTo(from.getAbsolutePath());
    }

    public void copyStreamToContainer(String containerId, File from, File to) {
        dockerClient.container()
            .withName(containerId)
            .archive()
            .uploadTo(to.getAbsolutePath())
            .withHostResource(from.getAbsolutePath());
    }

    public void connectToNetwork(String networkId, String containerID) {
        this.dockerClient.network().withName(networkId).connect(containerID);
    }

    public void copyLog(String containerId, boolean follow, boolean stdout, boolean stderr, boolean timestamps, int tail,
        OutputStream outputStream) throws IOException {

        final SinceContainerOutputErrorTimestampsTailingLinesUsingListenerFollowDisplayInterface<OutputHandle> logs =
            dockerClient.container().withName(containerId).logs();

        logs.writingOutput(outputStream)
            .writingError(outputStream)
            .tailingLines(tail);
        if (timestamps) {
            logs.withTimestamps();
        }
        if (follow) {
            logs.follow();
        } else {
            logs.display();
        }
    }

    private void readDockerRawStream(InputStream rawSteram, OutputStream outputStream) throws IOException {
        byte[] header = new byte[8];
        while (rawSteram.read(header) > 0) {
            ByteBuffer headerBuffer = ByteBuffer.wrap(header);

            // Stream type
            byte type = headerBuffer.get();
            // SKip 3 bytes
            headerBuffer.get();
            headerBuffer.get();
            headerBuffer.get();
            // Payload frame size
            int size = headerBuffer.getInt();

            byte[] streamOutputBuffer = new byte[size];
            rawSteram.read(streamOutputBuffer);
            outputStream.write(streamOutputBuffer);
        }
    }

    private String readDockerRawStreamToString(InputStream rawStream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        readDockerRawStream(rawStream, output);
        return new String(output.toByteArray());
    }

    public String createNetwork(String id, Network network) {

        final InlineNetworkCreate inlineNetworkCreate = this.dockerClient.network().createNew().withName(id);

        if (network.getDriver() != null) {
            inlineNetworkCreate.withDriver(network.getDriver());
        }

        if (network.getIpam() != null) {
            inlineNetworkCreate.withIPAM(new IPAM(createIpamConfig(network), network.getDriver()));
        }

        if (network.getOptions() != null && !network.getOptions().isEmpty()) {
            inlineNetworkCreate.withOptions(network.getOptions());
        }

        final NetworkCreateResponse networkCreateResponse = inlineNetworkCreate.done();

        return networkCreateResponse.getId();
    }

    public void removeNetwork(String id) {
        this.dockerClient.network().withName(id).delete();
    }

    public List<NetworkResource> getNetworks() {
        return this.dockerClient.network().list().all();
    }

    private List<io.fabric8.docker.api.model.IPAMConfig> createIpamConfig(Network network) {
        List<io.fabric8.docker.api.model.IPAMConfig> ipamConfigs = new ArrayList<>();
        List<IPAMConfig> IPAMConfigs = network.getIpam().getIpamConfigs();

        if (IPAMConfigs != null) {
            for (IPAMConfig ipamConfig : IPAMConfigs) {
                io.fabric8.docker.api.model.IPAMConfig config =
                    new io.fabric8.docker.api.model.IPAMConfig();
                if (ipamConfig.getGateway() != null) {
                    config.setGateway(ipamConfig.getGateway());
                }
                if (ipamConfig.getIpRange() != null) {
                    config.setIPRange(ipamConfig.getIpRange());
                }
                if (ipamConfig.getSubnet() != null) {
                    config.setSubnet(ipamConfig.getSubnet());
                }
                ipamConfigs.add(config);
            }
        }

        return ipamConfigs;
    }

    /**
     * Get the URI of the docker host
     */
    public URI getDockerUri() {
        return dockerUri;
    }

    public DockerClient getDockerClient() {
        return this.dockerClient;
    }

    public String getDockerServerIp() {
        return dockerServerIp;
    }

    public static class ExecInspection {
        private CubeOutput output;
        private ContainerInspect containerInspect;

        public ExecInspection(CubeOutput output, ContainerInspect containerInspect) {
            this.output = output;
            this.containerInspect = containerInspect;
        }

        public CubeOutput getOutput() {
            return output;
        }

        public ContainerInspect getContainerInspect() {
            return containerInspect;
        }
    }

    //private static class OutputStreamLogsResultCallback
    //    extends ResultCallbackTemplate<LogContainerResultCallback, Frame> {
    //
    //    private OutputStream outputStream;
    //
    //    public OutputStreamLogsResultCallback(OutputStream outputStream) {
    //        this.outputStream = outputStream;
    //    }
    //
    //    @Override
    //    public void onNext(Frame object) {
    //        try {
    //            this.outputStream.write(object.getPayload());
    //            this.outputStream.flush();
    //        } catch (IOException e) {
    //            throw new RuntimeException(e);
    //        }
    //    }
    //}
}
