package com.cloud.app;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.sql.DataSource;

/**
 * Smoke test: verifica que todo el cableado de Spring (controllers, services, MyBatis, clientes AWS)
 * arranca sin errores. El DataSource real solo existe en runtime (prod: Secrets Manager, dev: Postgres
 * local), asi que aqui se reemplaza por un mock para que el test corra en CI sin BD ni credenciales AWS.
 */
@SpringBootTest(properties = "spring.sql.init.mode=never")
class CloudApplicationTests {

	@MockitoBean
	private DataSource dataSource;

	@Test
	void contextLoads() {
	}

}
