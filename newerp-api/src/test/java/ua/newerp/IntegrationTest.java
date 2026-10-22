package ua.newerp;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Base for tests that start the whole app against a throwaway MySQL in Docker with the same tables as newerp_dev
 * (db/schema.sql, db/reconstructed.sql, db/erp.sql). One container is shared by all test classes
 * ("singleton container"), so Spring can reuse one application context. Skipped when Docker is not running.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
public abstract class IntegrationTest {

    @ServiceConnection // Spring takes the database URL, user and password from this container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("newerp_test")
            .withCopyFileToContainer(MountableFile.forHostPath("../db/schema.sql"), "/docker-entrypoint-initdb.d/1-schema.sql")
            .withCopyFileToContainer(MountableFile.forHostPath("../db/reconstructed.sql"), "/docker-entrypoint-initdb.d/2-reconstructed.sql")
            .withCopyFileToContainer(MountableFile.forHostPath("../db/erp.sql"), "/docker-entrypoint-initdb.d/3-erp.sql");

    static {
        if (DockerClientFactory.instance().isDockerAvailable()) {
            MYSQL.start();
        }
    }
}
