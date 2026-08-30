package com.akshat.marcellus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MarcellusApplication {
	public static void main(String[] args) {
		String mongoUri = System.getenv("MONGO_URI");

		// 1. Check if Render is failing to pass the variable at all
		if (mongoUri == null || mongoUri.trim().isEmpty()) {
			throw new IllegalStateException("\n\n=== DEPLOYMENT FAILED: MONGO_URI environment variable is NULL or EMPTY! ===\n\n");
		}

		// 2. Check if the string is malformed
		if (!mongoUri.startsWith("mongodb")) {
			throw new IllegalStateException("\n\n=== DEPLOYMENT FAILED: MONGO_URI must start with 'mongodb://' or 'mongodb+srv://' ===\nFound: " + mongoUri + "\n\n");
		}

		// 3. Force Spring Boot to use the valid string
		System.setProperty("spring.data.mongodb.uri", mongoUri);
		SpringApplication.run(MarcellusApplication.class, args);
	}
}