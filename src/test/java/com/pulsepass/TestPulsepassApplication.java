package com.pulsepass;

import org.springframework.boot.SpringApplication;

public class TestPulsepassApplication {

	public static void main(String[] args) {
		SpringApplication.from(PulsepassApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
