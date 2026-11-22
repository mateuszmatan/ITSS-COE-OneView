package com.bbh.itss.coe.oneview;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OneViewApplication {

    public static void main(String[] args) {
        SpringApplication.run(OneViewApplication.class, args);
    }
}
