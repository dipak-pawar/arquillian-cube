/**
 * Copyright 2005-2016 Red Hat, Inc.
 * <p>
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.arquillian.cube.kubernetes.impl;

import io.fabric8.kubernetes.api.model.ReplicationController;
import io.fabric8.kubernetes.api.model.ReplicationControllerList;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.arquillian.cube.kubernetes.annotations.Named;
import org.arquillian.cube.kubernetes.impl.requirement.RequiresKubernetes;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(Arquillian.class)
@Category(RequiresKubernetes.class)
public class ReplicationControllerInjection {

    @ArquillianResource
    private KubernetesClient client;

    @ArquillianResource
    private ReplicationControllerList controllerList;

    @Named("test-controller")
    @ArquillianResource
    private ReplicationController controller;

    @Named(value = "test-controller-second", namespace = "test-secondary-namespace")
    @ArquillianResource
    private ReplicationController controllerInSecondaryNamespace;

    @Test
    public void testReplicationControllersInjection() {
        assertNotNull(controllerList);
        assertEquals(1, controllerList.getItems().size());
        assertEquals("test-controller", controllerList.getItems().get(0).getMetadata().getName());

        assertNotNull(controller);
        assertEquals("test-controller", controller.getMetadata().getName());

        assertNotNull(controller);
        assertEquals("test-controller-second", controllerInSecondaryNamespace.getMetadata().getName());
        assertEquals("test-secondary-namespace", controllerInSecondaryNamespace.getMetadata().getNamespace());
    }
}
