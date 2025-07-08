package com.example.monarch.evaluator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EvaluatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(EvaluatorApplication.class, args);
    }
}
