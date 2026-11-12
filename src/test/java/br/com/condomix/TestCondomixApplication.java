package br.com.condomix;

import org.springframework.boot.SpringApplication;

public class TestCondomixApplication {

	public static void main(String[] args) {
		SpringApplication.from(CondomixApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
