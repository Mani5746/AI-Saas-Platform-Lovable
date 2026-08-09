package com.codingshuttleproject.lovableclone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;



@SpringBootApplication
@EntityScan("com.codingshuttleproject.lovableclone.entity")
public class LovablecloneApplication {

	public static void main(String[] args) {
		SpringApplication.run(LovablecloneApplication.class, args);

	}

}
