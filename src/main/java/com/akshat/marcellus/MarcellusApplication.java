package com.akshat.marcellus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MarcellusApplication {

	public static void main(String[] args) {
		// 1. Read the secret variable directly from Render's OS
		String mongoUri = System.getenv("MONGO_URI");

		// 2. If it exists, force Spring Boot to use it (bypassing properties files)
		if (mongoUri != null && !mongoUri.isEmpty()) {
			System.setProperty("spring.data.mongodb.uri", mongoUri);
		} else {
			System.out.println("WARNING: MONGO_URI environment variable is missing!");
		}

		SpringApplication.run(MarcellusApplication.class, args);
	}
}