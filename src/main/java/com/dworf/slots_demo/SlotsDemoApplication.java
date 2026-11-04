package com.dworf.slots_demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Map;

@SpringBootApplication
public class SlotsDemoApplication
{
	// I can interact with project files from idea, and u can review my changes and implement yours right in antigravity IDE
	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(SlotsDemoApplication.class);
		// Defaults to the local H2 "test" profile; deployments set SPRING_PROFILES_ACTIVE=prod
		app.setDefaultProperties(Map.of("spring.profiles.default", "test"));
		app.run(args);
	}
}

