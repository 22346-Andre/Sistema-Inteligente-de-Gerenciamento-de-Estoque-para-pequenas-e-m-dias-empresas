package com.smartstock.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class BackendApplicationTests {

	@DynamicPropertySource
	static void testProperties(DynamicPropertyRegistry registry) {
		TestRsaKeys.register(registry);
	}

	@Test
	void contextLoads() {
	}

}