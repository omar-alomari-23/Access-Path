package com.accesspath;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AccessPathApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccessPathApplication.class, args);
    }
}