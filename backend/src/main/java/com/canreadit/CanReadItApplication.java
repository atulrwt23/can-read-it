package com.canreadit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CanReadItApplication {

    public static void main(String[] args) {
        SpringApplication.run(CanReadItApplication.class, args);
    }
}
