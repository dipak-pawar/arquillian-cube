package org.arquillian.cube.kubernetes.impl.enricher;

import io.fabric8.kubernetes.clnt.v3_1.KubernetesClient;
import java.lang.annotation.Annotation;
import org.arquillian.cube.kubernetes.impl.DefaultSession;
import org.jboss.arquillian.core.api.Instance;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.arquillian.test.spi.TestEnricher;

public abstract class AbstractKubernetesTestEnricher implements TestEnricher {

    @Inject
    private Instance<KubernetesClient> client;

    @Inject
    private Instance<DefaultSession> session;


    protected KubernetesClient getClient() {
        return client.get();
    }

    protected DefaultSession getSession() {
        return session.get();
    }

    protected <T extends Annotation> T getAnnotation(Class<T> annotationClass, Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (annotation.annotationType() == annotationClass) {
                return annotationClass.cast(annotation);
            }
        }
        return null;
    }
}
