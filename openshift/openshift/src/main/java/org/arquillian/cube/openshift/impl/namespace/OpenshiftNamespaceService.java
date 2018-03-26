package org.arquillian.cube.openshift.impl.namespace;

import io.fabric8.kubernetes.api.model.v3_1.Namespace;
import io.fabric8.kubernetes.clnt.v3_1.KubernetesClient;
import io.fabric8.kubernetes.clnt.v3_1.KubernetesClientException;
import io.fabric8.openshift.api.model.v3_1.Project;
import io.fabric8.openshift.api.model.v3_1.ProjectRequest;
import io.fabric8.openshift.api.model.v3_1.ProjectRequestBuilder;
import io.fabric8.openshift.clnt.v3_1.DefaultOpenShiftClient;
import io.fabric8.openshift.clnt.v3_1.OpenShiftClient;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.arquillian.cube.impl.util.Timespan;
import org.arquillian.cube.kubernetes.api.Configuration;
import org.arquillian.cube.kubernetes.api.LabelProvider;
import org.arquillian.cube.kubernetes.api.Logger;
import org.arquillian.cube.kubernetes.api.NamespaceService;
import org.arquillian.cube.kubernetes.impl.namespace.DefaultNamespaceService;

import static org.arquillian.cube.impl.util.Timespan.TimeUnit.SECOND;

public class OpenshiftNamespaceService extends DefaultNamespaceService {

    @Override
    public NamespaceService toImmutable() {
        if (delegate != null) {
            return delegate;
        }
        synchronized (this) {
            if (delegate == null) {
                delegate = new ImmutableOpenshiftNamespaceService(client.get(),
                    configuration.get(),
                    labelProvider.get().toImmutable(),
                    logger.get().toImmutable()
                );
            }
        }
        return delegate;
    }

    public static class ImmutableOpenshiftNamespaceService extends DefaultNamespaceService.ImmutableNamespaceService {

        public ImmutableOpenshiftNamespaceService(KubernetesClient client, Configuration configuration,
            LabelProvider labelProvider, Logger logger) {
            super(client, configuration, labelProvider, logger);
        }

        @Override
        public Namespace create(String namespace, Map<String, String> annotations) {
            if (client.isAdaptable(OpenShiftClient.class)) {
                OpenShiftClient openShiftClient = client.adapt(OpenShiftClient.class);
                logger.status("Creating project: " + namespace);
                ProjectRequest projectRequest = new ProjectRequestBuilder()
                    .withNewMetadata()
                    .withName(namespace)
                    .withAnnotations(annotations)
                    .addToLabels(labelProvider.getLabels())
                    .addToLabels(PROJECT_LABEL, client.getNamespace())
                    .addToLabels(FRAMEWORK_LABEL, ARQUILLIAN_FRAMEWORK)
                    .addToLabels(COMPONENT_LABEL, ITEST_COMPONENT)
                    .endMetadata()
                    .build();

                ProjectRequest request = openShiftClient.projectrequests().create(projectRequest);
                logger.info("To switch to the new project: oc project " + namespace);
                return openShiftClient.namespaces().withName(request.getMetadata().getName()).get();
            } else {
                return super.create(namespace, annotations);
            }
        }

        private void waitUntilProjectDeleted(String namespace) {
            final DefaultOpenShiftClient openShiftClient = new DefaultOpenShiftClient();

            Project project = openShiftClient.projects().withName(namespace).get();
            int retryCount = 0;
            while (project != null && project.getStatus().getPhase().equals("Terminating") && retryCount < 60) {
                try {
                    TimeUnit.MILLISECONDS.sleep(500);
                    project = openShiftClient.projects().withName(namespace).get();
                    retryCount++;
                    if (retryCount == 60 && project != null && project.getStatus().getPhase().equals("Terminating")) {
                        logger.warn("Project is in `Terminating` state after deleting and waiting for 30s");
                    }
                } catch (InterruptedException e) {
                   throw new RuntimeException(e);
                }
            }
        }
        @Override
        public Namespace create(String namespace) {
           return create(namespace, Collections.emptyMap());
        }

        @Override
        public Boolean delete(String namespace) {
            if (client.isAdaptable(OpenShiftClient.class)) {

                logger.info("Deleting project: " + namespace + "...");
                OpenShiftClient openShiftClient = client.adapt(OpenShiftClient.class);
                Boolean deleted = openShiftClient.projects().withName(namespace).withGracePeriod(0).delete();
                if (deleted) {
                    logger.info("Project: " + namespace + ", successfully deleted");
                    waitUntilProjectDeleted(namespace);
                } else {
                    logger.info("Failed to delete Project: " + namespace);
                }
                return deleted;
            } else {
                return super.delete(namespace);
            }
        }

        @Override
        public Boolean exists(String namespace) {
            if (client.isAdaptable(OpenShiftClient.class)) {
                OpenShiftClient openShiftClient = client.adapt(OpenShiftClient.class);
                try {
                    // It is preferable to iterate on the list of projects as regular user
                    // with the 'basic-role' bound are not granted permission get operation
                    // on non-existing project resource that returns 403 instead of 404.
                    // Only more privileged roles like 'view' or 'cluster-reader'
                    // are granted this permission.
                    return openShiftClient.projects().list().getItems().stream()
                        .map(project -> project.getMetadata().getName())
                        .anyMatch(Predicate.isEqual(namespace));
                } catch (KubernetesClientException e) {
                    logger.warn(
                        String.format("Next exception has been thrown while checking the existence of % namespace and the namespace is going to be created. The exception is %s",
                            namespace, e.toString())
                    );
                    return false;
                }
            } else {
                return super.exists(namespace);
            }
        }

        @Override
        public Namespace annotate(String namespace, Map<String, String> annotations) {
            if (client.isAdaptable(OpenShiftClient.class)) {
                OpenShiftClient openShiftClient = client.adapt(OpenShiftClient.class);

                /* FIXME: Openshift currently doesn't support annotations
                 * See: https://github.com/openshift/origin/issues/3819
                 * And: https://github.com/openshift/origin/issues/10315
                 *
                 * https://github.com/arquillian/arquillian-cube/issues/740

                openShiftClient.projects().withName(namespace)
                    .edit()
                    .editMetadata()
                    .addToAnnotations(annotations)
                    .endMetadata()
                    .done();
                */

                return openShiftClient.namespaces().withName(namespace).get();
            } else {
                return super.annotate(namespace, annotations);
            }
        }

        @Override
        @Deprecated // The method is redundant (since its called always before destroy).
        public void clean(String namespace) {
            if (client.isAdaptable(OpenShiftClient.class)) {
                OpenShiftClient openShiftClient = client.adapt(OpenShiftClient.class);
                openShiftClient.deploymentConfigs().inNamespace(namespace).delete();
            }

            super.clean(namespace);
        }
    }
}
