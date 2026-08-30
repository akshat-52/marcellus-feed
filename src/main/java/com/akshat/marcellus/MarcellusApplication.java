package com.akshat.marcellus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Collections;

@SpringBootApplication
public class MarcellusApplication {
	public static void main(String[] args) {
		String mongoUri = System.getenv("MONGO_URI");

		if (mongoUri == null || mongoUri.trim().isEmpty()) {
			throw new IllegalStateException("MONGO_URI environment variable is missing!");
		}

		SpringApplication app = new SpringApplication(MarcellusApplication.class);
		// Force Spring Boot to register the URI into its environment before context creation
		app.setDefaultProperties(Collections.singletonMap("spring.data.mongodb.uri", mongoUri));
		app.run(args);
	}
}