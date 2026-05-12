package dev.novapay.payments;

import org.springframework.boot.SpringApplication;

public class TestPaymentServiceApplication {

	static void main(String[] args) {
		SpringApplication.from(PaymentServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
