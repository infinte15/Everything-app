package com.Finn.everything_app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableJpaAuditing
@EnableAsync
public class EverythingAppApplication {

	public static void main(String[] args) {
		SpringApplication.run(EverythingAppApplication.class, args);
		System.out.println("==============================================");
		System.out.println("🚀 Everything App successfully started!");
		System.out.println("📍 API available at: http://localhost:8080/api");
		System.out.println("==============================================");
	}
}