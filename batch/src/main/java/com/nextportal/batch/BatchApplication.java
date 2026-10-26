package com.nextportal.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.nextportal.common")
@EntityScan("com.nextportal.common")
@EnableJpaRepositories("com.nextportal.common")
public class BatchApplication {
    public static void main(String[] args) { SpringApplication.run(BatchApplication.class, args); }
}
