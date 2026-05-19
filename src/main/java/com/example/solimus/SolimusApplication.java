package com.example.solimus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SolimusApplication {

	public static void main(String[] args) {
		SpringApplication.run(SolimusApplication.class, args);
	}

}
