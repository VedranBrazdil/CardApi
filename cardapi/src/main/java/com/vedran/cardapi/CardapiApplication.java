package com.vedran.cardapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

//(exclude = {HibernateJpaAutoConfiguration.class})
//(exclude={DataSourceAutoConfiguration.class})
@SpringBootApplication
public class CardapiApplication {

	public static void main(String[] args) {
		SpringApplication.run(CardapiApplication.class, args);
	}

}
