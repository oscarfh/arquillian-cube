package org.arquillian.cube.docker.impl.docker;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.ProcessingException;

import org.apache.http.conn.UnsupportedSchemeException;
import org.arquillian.cube.TopContainer;
import org.arquillian.cube.docker.impl.client.CubeDockerConfiguration;
import org.arquillian.cube.docker.impl.client.config.BuildImage;
import org.arquillian.cube.docker.impl.client.config.CubeContainer;
import org.arquillian.cube.docker.impl.client.config.Image;
import org.arquillian.cube.docker.impl.client.config.PortBinding;
import org.arquillian.cube.docker.impl.util.BindingUtil;
import org.arquillian.cube.docker.impl.util.HomeResolverUtil;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.NotFoundException;
import com.github.dockerjava.api.command.BuildImageCmd;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.LogContainerCmd;
import com.github.dockerjava.api.command.PingCmd;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.command.TopContainerResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.ChangeLog;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Device;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.InternetProtocol;
import com.github.dockerjava.api.model.Link;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.Ports.Binding;
import com.github.dockerjava.api.model.RestartPolicy;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.api.model.VolumesFrom;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig.DockerClientConfigBuilder;
import com.github.dockerjava.core.async.ResultCallbackTemplate;
import com.github.dockerjava.core.command.BuildImageResultCallback;
import com.github.dockerjava.core.command.LogContainerResultCallback;
import com.github.dockerjava.core.command.PullImageResultCallback;

public class DockerClientExecutor {

    private static final String DEFAULT_C_GROUPS_PERMISSION = "rwm";
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
    public static final String DOMAINNAME= "domainName";

    private static final Logger log = Logger.getLogger(DockerClientExecutor.class.getName());
    private static final Pattern IMAGEID_PATTERN = Pattern.compile(".*Successfully built\\s(\\p{XDigit}+)");

    private DockerClient dockerClient;
    private CubeDockerConfiguration cubeConfiguration;
    private final URI dockerUri;
    private final String dockerServerIp;

    public DockerClientExecutor(CubeDockerConfiguration cubeConfiguration) {
        DockerClientConfigBuilder configBuilder =
            DockerClientConfig.createDefaultConfigBuilder();

        String dockerServerUri = cubeConfiguration.getDockerServerUri();

        dockerUri = URI.create(dockerServerUri);
        dockerServerIp = cubeConfiguration.getDockerServerIp();

        configBuilder.withVersion(cubeConfiguration.getDockerServerVersion()).withUri(dockerUri.toString());
        if(cubeConfiguration.getUsername() != null) {
            configBuilder.withUsername(cubeConfiguration.getUsername());
        }

        if(cubeConfiguration.getPassword() != null) {
            configBuilder.withPassword(cubeConfiguration.getPassword());
        }

        if(cubeConfiguration.getEmail() != null) {
            configBuilder.withEmail(cubeConfiguration.getEmail());
        }

        if(cubeConfiguration.getCertPath() != null) {
            configBuilder.withDockerCertPath(HomeResolverUtil.resolveHomeDirectoryChar(cubeConfiguration.getCertPath()));
        }

        this.dockerClient = DockerClientBuilder.getInstance(configBuilder.build()).build();
        this.cubeConfiguration = cubeConfiguration;
    }


    public List<Container> listRunningContainers() {
        return this.dockerClient.listContainersCmd().exec();
    }

    public String createContainer(String name, CubeContainer containerConfiguration) {

        // we check if Docker server is up and correctly configured.
        this.pingDockerServer();

        String image = getImageName(containerConfiguration);

        CreateContainerCmd createContainerCmd = this.dockerClient.createContainerCmd(image);
        createContainerCmd.withName(name);

        Set<ExposedPort> allExposedPorts = resolveExposedPorts(containerConfiguration, createContainerCmd);
        if (!allExposedPorts.isEmpty()) {
            int numberOfExposedPorts = allExposedPorts.size();
            createContainerCmd.withExposedPorts(allExposedPorts.toArray(new ExposedPort[numberOfExposedPorts]));
        }

        if(containerConfiguration.getReadonlyRootfs() != null) {
            createContainerCmd.withReadonlyRootfs(containerConfiguration.getReadonlyRootfs());
        }

        if(containerConfiguration.getLabels() != null) {
            createContainerCmd.withLabels(containerConfiguration.getLabels());
        }

        if (containerConfiguration.getWorkingDir() != null) {
            createContainerCmd.withWorkingDir(containerConfiguration.getWorkingDir());
        }

        if (containerConfiguration.getDisableNetwork() != null) {
            createContainerCmd.withNetworkDisabled(containerConfiguration.getDisableNetwork());
        }

        if (containerConfiguration.getHostName() != null) {
            createContainerCmd.withHostName(containerConfiguration.getHostName());
        }

        if (containerConfiguration.getPortSpecs() != null) {
            createContainerCmd.withPortSpecs(containerConfiguration.getPortSpecs().toArray(new String[0]));
        }

        if (containerConfiguration.getUser() != null) {
            createContainerCmd.withUser(containerConfiguration.getUser());
        }

        if(containerConfiguration.getTty() != null) {
            createContainerCmd.withTty(containerConfiguration.getTty());
        }
        if(containerConfiguration.getStdinOpen() != null) {
            createContainerCmd.withStdinOpen(containerConfiguration.getStdinOpen());
        }

        if (containerConfiguration.getStdinOnce() != null) {
            createContainerCmd.withStdInOnce(containerConfiguration.getStdinOnce());
        }

        if (containerConfiguration.getMemoryLimit() != null) {
            createContainerCmd.withMemoryLimit(containerConfiguration.getMemoryLimit());
        }

        if (containerConfiguration.getMemorySwap() != null) {
            createContainerCmd.withMemorySwap(containerConfiguration.getMemorySwap());
        }

        if (containerConfiguration.getCpuShares() != null) {
            createContainerCmd.withCpuShares(containerConfiguration.getCpuShares());
        }

        if(containerConfiguration.getCpuSet() != null) {
            createContainerCmd.withCpuset(containerConfiguration.getCpuSet());
        }

        if (containerConfiguration.getAttachStdin() != null) {
            createContainerCmd.withAttachStdin(containerConfiguration.getAttachStdin());
        }

        if (containerConfiguration.getAttachSterr() != null) {
            createContainerCmd.withAttachStderr(containerConfiguration.getAttachSterr());
        }

        if (containerConfiguration.getEnv() != null) {
            createContainerCmd.withEnv(resolveDockerServerIpInList(containerConfiguration.getEnv()).toArray(new String[0]));
        }

        if (containerConfiguration.getCmd() != null) {
            createContainerCmd.withCmd(containerConfiguration.getCmd().toArray(new String[0]));
        }

        if (containerConfiguration.getDns() != null) {
            createContainerCmd.withDns(containerConfiguration.getDns().toArray(new String[0]));
        }

        if (containerConfiguration.getVolumes() != null) {
            createContainerCmd.withVolumes(toVolumes(containerConfiguration.getVolumes()));
        }

        if (containerConfiguration.getVolumesFrom() != null) {
            createContainerCmd.withVolumesFrom(toVolumesFrom(containerConfiguration.getVolumesFrom()));
        }

        if (containerConfiguration.getBinds() != null) {
            createContainerCmd.withBinds(toBinds(containerConfiguration.getBinds()));
        }

        if (containerConfiguration.getLinks() != null) {
            createContainerCmd.withLinks(toLinks(containerConfiguration.getLinks()));
        }

        if (containerConfiguration.getPortBindings() != null) {
            createContainerCmd.withPortBindings(toPortBindings(containerConfiguration.getPortBindings()));
        }

        if (containerConfiguration.getPrivileged() != null) {
            createContainerCmd.withPrivileged(containerConfiguration.getPrivileged());
        }

        if (containerConfiguration.getPublishAllPorts() != null) {
            createContainerCmd.withPublishAllPorts(containerConfiguration.getPublishAllPorts());
        }

        if (containerConfiguration.getNetworkMode() != null) {
            createContainerCmd.withNetworkMode(containerConfiguration.getNetworkMode());
        }

        if (containerConfiguration.getDnsSearch() != null) {
            createContainerCmd.withDnsSearch(containerConfiguration.getDnsSearch().toArray(new String[0]));
        }

        if (containerConfiguration.getDevices() != null) {
            createContainerCmd.withDevices(toDevices(containerConfiguration.getDevices()));
        }

        if (containerConfiguration.getRestartPolicy() != null) {
            createContainerCmd.withRestartPolicy(toRestartPolicy(containerConfiguration.getRestartPolicy()));
        }

        if (containerConfiguration.getCapAdd() != null) {
            createContainerCmd.withCapAdd(toCapability(containerConfiguration.getCapAdd()));
        }

        if (containerConfiguration.getCapDrop() != null) {
            createContainerCmd.withCapDrop(toCapability(containerConfiguration.getCapDrop()));
        }

        if(containerConfiguration.getExtraHosts() != null) {
            createContainerCmd.withExtraHosts(containerConfiguration.getExtraHosts().toArray(new String[0]));
        }
        if(containerConfiguration.getEntryPoint() != null) {
            createContainerCmd.withEntrypoint(containerConfiguration.getEntryPoint().toArray(new String[0]));
        }

        if(containerConfiguration.getDomainName() != null) {
            createContainerCmd.withDomainName(containerConfiguration.getDomainName());
        }

        boolean alwaysPull = false;

        if (containerConfiguration.getAlwaysPull() != null) {
            alwaysPull = containerConfiguration.getAlwaysPull();
        }

        if ( alwaysPull ) {
            log.info(String.format(
                        "Pulling latest Docker Image %s.", image));
            this.pullImage(image);
        }

        try {
            return createContainerCmd.exec().getId();
        } catch (NotFoundException e) {
            if ( !alwaysPull ) {
                log.warning(String.format(
                        "Docker Image %s is not on DockerHost and it is going to be automatically pulled.", image));
                this.pullImage(image);
                return createContainerCmd.exec().getId();
            } else {
                throw e;
            }
        } catch (ProcessingException e) {
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
        }
    }

    private List<String> resolveDockerServerIpInList(Collection<String> envs) {
        List<String> resolvedEnv = new ArrayList<String>();
        for (String env : envs) {
            if(env.contains(CubeDockerConfiguration.DOCKER_SERVER_IP)) {
                resolvedEnv.add(env.replaceAll(CubeDockerConfiguration.DOCKER_SERVER_IP, cubeConfiguration.getDockerServerIp()));
            } else {
                resolvedEnv.add(env);
            }
        }
        return resolvedEnv;
    }

    private Set<ExposedPort> resolveExposedPorts(CubeContainer containerConfiguration,
            CreateContainerCmd createContainerCmd) {
        Set<ExposedPort> allExposedPorts = new HashSet<>();
        if (containerConfiguration.getPortBindings() != null) {
            for(PortBinding binding : containerConfiguration.getPortBindings()) {
                allExposedPorts.add(new ExposedPort(binding.getExposedPort().getExposed(), InternetProtocol.parse(binding.getExposedPort().getType())));
            }
        }
        if (containerConfiguration.getExposedPorts() != null) {
            for(org.arquillian.cube.docker.impl.client.config.ExposedPort port : containerConfiguration.getExposedPorts()) {
                allExposedPorts.add(new ExposedPort(port.getExposed(), InternetProtocol.parse(port.getType())));
            }
        }
        return allExposedPorts;
    }

    private String getImageName(CubeContainer containerConfiguration) {
        String image;

        if (containerConfiguration.getImage() != null) {
            image = containerConfiguration.getImage().toImageRef();
        } else {

            if (containerConfiguration.getBuildImage() != null) {

                BuildImage buildImage = containerConfiguration.getBuildImage();

                if (buildImage.getDockerfileLocation() != null) {
                    Map<String, Object> params = new HashMap<String, Object>(); //(containerConfiguration, BUILD_IMAGE);
                    params.put("noCache", buildImage.isNoCache());
                    params.put("remove", buildImage.isRemove());
                    params.put("dockerFileLocation", buildImage.getDockerfileLocation());
                    params.put("dockerFileName", buildImage.getDockerfileName());

                    image = this.buildImage(buildImage.getDockerfileLocation(), params);
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
        StartContainerCmd startContainerCmd = this.dockerClient.startContainerCmd(id);

        startContainerCmd.exec();
    }

    private Ports toPortBindings(Collection<PortBinding> portBindings) {
        Ports ports = new Ports();
        for (PortBinding portBinding : portBindings) {
            ports.bind(
                    new ExposedPort(
                            portBinding.getExposedPort().getExposed(),
                            InternetProtocol.parse(portBinding.getExposedPort().getType())),
                    new Binding(portBinding.getHost(), portBinding.getBound()));
        }
        return ports;
    }

    public void stopContainer(String containerId) {
        this.dockerClient.stopContainerCmd(containerId).exec();
    }

    public void removeContainer(String containerId) {
        this.dockerClient.removeContainerCmd(containerId).exec();
    }

    public InspectContainerResponse inspectContainer(String containerId) {
        return this.dockerClient.inspectContainerCmd(containerId).exec();
    }

    public int waitContainer(String containerId) {
        return this.dockerClient.waitContainerCmd(containerId).exec();
    }

    public void pingDockerServer() {
        try {
            PingCmd pingCmd = this.dockerClient.pingCmd();
            pingCmd.exec();
        } catch (ProcessingException e) {
            if (e.getCause() instanceof ConnectException) {
                throw new IllegalStateException(
                        String.format(
                                "Docker server is not running in %s host or it does not accept connections in tcp protocol, read https://github.com/arquillian/arquillian-cube#preliminaries to learn how to enable it.",
                                this.cubeConfiguration.getDockerServerUri()), e);
            }
        }
    }

    public String buildImage(String location, Map<String, Object> params) {

        BuildImageCmd buildImageCmd = createBuildCommand(location);
        configureBuildCommand(params, buildImageCmd);

        String imageId = buildImageCmd.exec(new BuildImageResultCallback()).awaitImageId();

        if (imageId == null) {
            throw new IllegalStateException(
                    String.format(
                            "Docker server has not provided an imageId for image build from %s.",
                            location));
        }

        return imageId.trim();
    }

    public static String getImageId(String fullLog) {
        Matcher m = IMAGEID_PATTERN.matcher(fullLog);
        String imageId = null;
        if (m.find()) {
            imageId = m.group(1);
        }
        return imageId;
    }

    private void configureBuildCommand(Map<String, Object> params, BuildImageCmd buildImageCmd) {
        if (params.containsKey(NO_CACHE)) {
            buildImageCmd.withNoCache((boolean) params.get(NO_CACHE));
        }

        if (params.containsKey(REMOVE)) {
            buildImageCmd.withRemove((boolean) params.get(REMOVE));
        }

        if(params.containsKey(DOCKERFILE_NAME)) {
            buildImageCmd.withDockerfile(new File((String) params.get(DOCKERFILE_NAME)));
        }
    }

    private BuildImageCmd createBuildCommand(String location) {
        BuildImageCmd buildImageCmd = null;

        try {
            URL url = new URL(location);
            buildImageCmd = this.dockerClient.buildImageCmd(url.openStream());
        } catch (MalformedURLException e) {
            // Means that it is not a URL so it can be a File or Directory
            File file = new File(location);

            if (file.exists()) {
                if (file.isDirectory()) {
                    buildImageCmd = this.dockerClient.buildImageCmd(file);
                } else {
                    try {
                        buildImageCmd = this.dockerClient.buildImageCmd(new FileInputStream(file));
                    } catch (FileNotFoundException notFoundFile) {
                        throw new IllegalArgumentException(notFoundFile);
                    }
                }
            }

        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
        return buildImageCmd;
    }

    public void pullImage(String imageName) {

        final Image image = Image.valueOf(imageName);

        PullImageCmd pullImageCmd = this.dockerClient.pullImageCmd(image.getName());

        if (this.cubeConfiguration.getDockerRegistry() != null) {
            pullImageCmd.withRegistry(this.cubeConfiguration.getDockerRegistry());
        }

        String tag = image.getTag();
        if (tag != null && !"".equals(tag)) {
            pullImageCmd.withTag(tag);
        }

        pullImageCmd.exec(new PullImageResultCallback()).awaitSuccess();

    }

    public String execStart(String containerId, String... commands) {
        ExecCreateCmdResponse execCreateCmdResponse = this.dockerClient.execCreateCmd(containerId)
                .withAttachStdout(true).withAttachStdin(false).withAttachStderr(false).withTty().withCmd(commands)
                .exec();
        InputStream consoleOutputStream = dockerClient.execStartCmd(execCreateCmdResponse.getId()).withDetach(false)
                .exec();
        String output;
        try {
            output = readDockerRawStreamToString(consoleOutputStream);
        } catch (IOException e) {
            return "";
        }
        return output;
    }

    public List<org.arquillian.cube.ChangeLog> inspectChangesOnContainerFilesystem(String containerId) {
        List<ChangeLog> changeLogs = dockerClient.containerDiffCmd(containerId).exec();
        List<org.arquillian.cube.ChangeLog> changes = new ArrayList<>();
        for (ChangeLog changeLog : changeLogs) {
            changes.add(new org.arquillian.cube.ChangeLog(changeLog.getPath(), changeLog.getKind()));
        }
        return changes;
    }

    public TopContainer top(String containerId) {
        TopContainerResponse topContainer = dockerClient.topContainerCmd(containerId).exec();
        return new TopContainer(topContainer.getTitles(), topContainer.getProcesses());
    }

    public InputStream getFileOrDirectoryFromContainerAsTar(String containerId, String from) {
        InputStream response = dockerClient.copyFileFromContainerCmd(containerId, from).exec();
        return response;
    }

    public void copyLog(String containerId, boolean follow, boolean stdout, boolean stderr, boolean timestamps, int tail, OutputStream outputStream) throws IOException {
        LogContainerCmd logContainerCmd = dockerClient.logContainerCmd(containerId).withStdErr().withStdOut();

        logContainerCmd.withFollowStream(follow);
        logContainerCmd.withStdOut(stdout);
        logContainerCmd.withStdErr(stderr);
        logContainerCmd.withTimestamps(timestamps);

        if(tail < 0) {
            logContainerCmd.withTailAll();
        } else {
            logContainerCmd.withTail(tail);
        }

        OutputStreamLogsResultCallback outputStreamLogsResultCallback = new OutputStreamLogsResultCallback(outputStream);
        logContainerCmd.exec(outputStreamLogsResultCallback);
        try {
            outputStreamLogsResultCallback.awaitCompletion();
        } catch (InterruptedException e) {
            throw new IOException(e);
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

    /**
     * Get the URI of the docker host
     *
     * @return
     */
    public URI getDockerUri() {
        return dockerUri;
    }

    private static final Device[] toDevices(Collection<org.arquillian.cube.docker.impl.client.config.Device> deviceList) {
        Device[] devices = new Device[deviceList.size()];

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

                devices[i] = new Device(cGroupPermissions, pathInContainer, pathOnHost);
                i++;
            }
        }

        return devices;
    }

    private static final RestartPolicy toRestartPolicy(org.arquillian.cube.docker.impl.client.config.RestartPolicy restart) {
        if (restart.getName() != null) {
            String name = restart.getName();

            if ("failure".equals(name)) {
                return RestartPolicy.onFailureRestart(restart.getMaximumRetryCount());
            } else {
                if ("restart".equals(name)) {
                    return RestartPolicy.alwaysRestart();
                } else {
                    return RestartPolicy.noRestart();
                }
            }

        } else {
            return RestartPolicy.noRestart();
        }
    }

    private static final Link[] toLinks(Collection<org.arquillian.cube.docker.impl.client.config.Link> linkList) {
        Link[] links = new Link[linkList.size()];
        int i=0;
        for (org.arquillian.cube.docker.impl.client.config.Link link : linkList) {
            links[i] = new Link(link.getName(), link.getAlias());
            i++;
        }

        return links;
    }

    private static final Capability[] toCapability(Collection<String> configuredCapabilities) {
        List<Capability> capabilities = new ArrayList<Capability>();
        for (String capability : configuredCapabilities) {
            capabilities.add(Capability.valueOf(capability));
        }
        return capabilities.toArray(new Capability[capabilities.size()]);
    }

    private static final Bind[] toBinds(Collection<String> bindsList) {

        Bind[] binds = new Bind[bindsList.size()];
        int i = 0;
        for (String bind: bindsList) {
            binds[i] = Bind.parse(bind);
            i++;
        }

        return binds;
    }

    private static final Volume[] toVolumes(Collection<String> volumesList) {
        Volume[] volumes = new Volume[volumesList.size()];

        int i = 0;
        for (String volume : volumesList) {
            volumes[i] = new Volume(volume);
            i++;
        }

        return volumes;
    }

    private static final VolumesFrom[] toVolumesFrom(Collection<String> volumesFromList) {
        VolumesFrom[] volumesFrom = new VolumesFrom[volumesFromList.size()];

        int i = 0;
        for(String volumesFromm : volumesFromList) {
            volumesFrom[i] = VolumesFrom.parse(volumesFromm);
            i++;
        }
        return volumesFrom;
    }

    public DockerClient getDockerClient() {
        return this.dockerClient;
    }

    public String getDockerServerIp() {
        return dockerServerIp;
    }

    private static class OutputStreamLogsResultCallback extends ResultCallbackTemplate<LogContainerResultCallback, Frame> {

        private OutputStream outputStream;

        public OutputStreamLogsResultCallback(OutputStream outputStream) {
            this.outputStream = outputStream;
        }

        @Override
        public void onNext(Frame object) {
            try {
                this.outputStream.write(object.getPayload());
                this.outputStream.flush();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
