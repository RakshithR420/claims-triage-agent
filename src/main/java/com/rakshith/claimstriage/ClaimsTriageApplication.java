package com.rakshith.claimstriage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ClaimsTriageApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClaimsTriageApplication.class, args);
    }
}
