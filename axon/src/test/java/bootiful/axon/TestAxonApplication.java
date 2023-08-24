package bootiful.axon;

import org.axonframework.test.server.AxonServerContainer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.devtools.restart.RestartScope;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestAxonApplication {

    @Bean
    @RestartScope
    @ServiceConnection (name = "axoniq/axonserver")
    AxonServerContainer axonServerContainer() {
        return new AxonServerContainer(DockerImageName.parse("axoniq/axonserver:latest-dev"));
    }

    @Bean
    @RestartScope
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:latest"));
    }

    public static void main(String[] args) {
        SpringApplication.from(AxonApplication::main).with(TestAxonApplication.class).run(args);
    }

}
