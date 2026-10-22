package ua.newerp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.MountableFile;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts the whole app against a throwaway MySQL in Docker with the same tables as newerp_dev
 * (db/schema.sql, db/reconstructed.sql, db/erp.sql). Skipped when Docker is not running.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ApplicationIntegrationTest {

    @Container
    @ServiceConnection // Spring takes the database URL, user and password from this container
    static final MySQLContainer mysql = new MySQLContainer("mysql:8.4")
            .withDatabaseName("newerp_test")
            .withCopyFileToContainer(MountableFile.forHostPath("../db/schema.sql"), "/docker-entrypoint-initdb.d/1-schema.sql")
            .withCopyFileToContainer(MountableFile.forHostPath("../db/reconstructed.sql"), "/docker-entrypoint-initdb.d/2-reconstructed.sql")
            .withCopyFileToContainer(MountableFile.forHostPath("../db/erp.sql"), "/docker-entrypoint-initdb.d/3-erp.sql");

    @Autowired
    MockMvcTester mvc;

    @Test
    void healthIsUpWithDatabase() {
        assertThat(mvc.get().uri("/health"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.status").isEqualTo("UP");
    }
}
