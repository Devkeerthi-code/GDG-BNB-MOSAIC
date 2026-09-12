package com.hruthikesh.ime;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class ImeApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImeApplication.class, args);
    }

}
