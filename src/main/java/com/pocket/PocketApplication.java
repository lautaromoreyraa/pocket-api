package com.pocket;

import com.pocket.config.PocketProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(PocketProperties.class)
public class PocketApplication {

    public static void main(String[] args) {
        SpringApplication.run(PocketApplication.class, args);
    }
}
