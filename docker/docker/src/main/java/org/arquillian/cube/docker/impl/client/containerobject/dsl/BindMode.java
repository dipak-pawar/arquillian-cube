package org.arquillian.cube.docker.impl.client.containerobject.dsl;


/**
 * Binding mode for volumes
 */
public enum BindMode {

    READ_ONLY(AccessMode.ro), READ_WRITE(AccessMode.rw);

    public final AccessMode accessMode;

    BindMode(AccessMode accessMode) {
        this.accessMode = accessMode;
    }

    public enum AccessMode {

        rw, ro;


        public static final AccessMode DEFAULT = rw;

        public static final AccessMode fromBoolean(boolean accessMode) {
            return accessMode ? rw : ro;
        }

        public final boolean toBoolean() {
            return this.equals(AccessMode.rw);
        }
    }
}
