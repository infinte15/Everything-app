package com.Finn.everything_app;

import com.Finn.everything_app.service.bank.EnableBankingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaAuditing
@EnableAsync
// Fuer den naechtlichen Bankabruf (BankSyncScheduler) - bis dahin gab es im Projekt keinen
// einzigen zeitgesteuerten Ablauf, die Entprellung der Neuplanung nutzt einen eigenen Executor.
@EnableScheduling
@EnableConfigurationProperties(EnableBankingProperties.class)
public class EverythingAppApplication {

	public static void main(String[] args) {
		SpringApplication.run(EverythingAppApplication.class, args);
		System.out.println("==============================================");
		System.out.println("🚀 Everything App successfully started!");
		System.out.println("📍 API available at: http://localhost:8080/api");
		System.out.println("==============================================");
	}
}