package com.example.campushub;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;

@SpringBootTest
@EnableNeo4jRepositories(basePackages = "com.example.campushub.repositories.neo4j")
@EnableJpaRepositories(basePackages = "com.example.campushub.repositories.jpa")
class CampushubApplicationTests {

	@Test
	void contextLoads() {
	}

}
