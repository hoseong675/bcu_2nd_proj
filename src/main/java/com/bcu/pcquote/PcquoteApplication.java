package com.bcu.pcquote;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling   // 새벽 가격 배치용
public class PcquoteApplication {

	public static void main(String[] args) {
		SpringApplication.run(PcquoteApplication.class, args);
	}

}
