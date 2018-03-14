package org.arquillian.cube.kubernetes;

import java.nio.file.Path;
import org.jboss.shrinkwrap.resolver.api.maven.embedded.BuiltProject;
import org.jboss.shrinkwrap.resolver.api.maven.embedded.EmbeddedMaven;
import org.jboss.shrinkwrap.resolver.api.maven.embedded.pom.equipped.ConfigurationDistributionStage;

public class ResourceGeneratorBuilder {

    private Path pom;
    private String[] goals = new String[] {"package", "fabric8:build", "fabric8:resource"};
    private String namespace;

    public ResourceGeneratorBuilder namespace(String namespace) {
        this.namespace = namespace;
        return this;
    }

    public ResourceGeneratorBuilder goals(String[] goals) {
        this.goals = goals;
        return this;
    }

    public ResourceGeneratorBuilder pluginConfigurationIn(Path pom) {
        this.pom = pom;
        return this;
    }

    public void build() {
        final ConfigurationDistributionStage distributionStage = EmbeddedMaven
            .forProject(pom.toFile())
            .setQuiet()
            .useDefaultDistribution()
            .setDebug(true)
            .setDebugLoggerLevel()
            .setGoals(goals)
            .addProperty("fabric8.namespace", namespace);

        if (System.getenv("JAVA_HOME") == null) {
            distributionStage.addShellEnvironment("JAVA_HOME", "/usr/lib/jvm/java-1.8.0");
        }

        final BuiltProject builtProject = distributionStage.ignoreFailure()
            .build();

        System.out.println(builtProject.getMavenLog());
        if (builtProject.getMavenBuildExitCode() != 0) {
            throw new IllegalStateException("Maven build has failed, see logs for details");
        }
    }
}
