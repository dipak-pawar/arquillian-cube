package org.arquillian.cube.kubernetes;

import org.arquillian.cube.kubernetes.impl.SessionManager;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public class ClassLoaderTest {

    @Test
    public void should_not_load_maven() {
        Assertions.assertThat(isRunningFromMaven()).isFalse();
    }

    private boolean isRunningFromMaven() {
        try {
            SessionManager.class.getClassLoader().loadClass("org.apache.maven.surefire.booter.ForkedBooter");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

}
