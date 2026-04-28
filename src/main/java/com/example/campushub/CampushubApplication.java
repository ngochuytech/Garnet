package com.example.campushub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class CampushubApplication {

	public static void main(String[] args) {
		SpringApplication.run(CampushubApplication.class, args);
	}

}
