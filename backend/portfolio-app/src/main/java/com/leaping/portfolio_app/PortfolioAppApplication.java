package com.leaping.portfolio_app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PortfolioAppApplication {

	public static void main(String[] args) {
		System.out.println("Portfolio App is running...");
		SpringApplication.run(PortfolioAppApplication.class, args);
	}

}
