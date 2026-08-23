package com.example.musing_BE;

import com.example.musing_BE.auth.config.AuthProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AuthProperties.class)
public class MusingBeApplication {

	public static void main(String[] args) {
		SpringApplication.run(MusingBeApplication.class, args);
	}

}
