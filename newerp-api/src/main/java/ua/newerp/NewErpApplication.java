package ua.newerp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point. It sits in the root package ua.newerp, so Spring finds components,
 * JPA entities and repositories in every layer below it.
 */
@SpringBootApplication
public class NewErpApplication {

    public static void main(String[] args) {
        SpringApplication.run(NewErpApplication.class, args);
    }
}
