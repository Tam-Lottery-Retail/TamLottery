package com.mtriet.tamlottery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TamLotteryBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(TamLotteryBackendApplication.class, args);
	}

}
