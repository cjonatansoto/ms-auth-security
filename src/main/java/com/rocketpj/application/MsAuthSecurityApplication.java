package com.rocketpj.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class MsAuthSecurityApplication {

	public static void main(String[] args) {
		SpringApplication.run(MsAuthSecurityApplication.class, args);
	}

}
