package ua.newerp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point. It sits in the root package ua.newerp, so Spring finds components,
 * JPA entities and repositories in every layer below it. @EnableScheduling runs the @Scheduled background jobs.
 */
@SpringBootApplication
@EnableScheduling
public class NewErpApplication {

    public static void main(String[] args) {
        SpringApplication.run(NewErpApplication.class, args);
    }
}
