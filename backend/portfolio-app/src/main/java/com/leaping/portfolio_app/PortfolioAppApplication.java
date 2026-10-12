package com.leaping.portfolio_app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PortfolioAppApplication {

	public static void main(String[] args) {
		System.out.println("Portfolio App is running...");
		SpringApplication.run(PortfolioAppApplication.class, args);
	}

}
