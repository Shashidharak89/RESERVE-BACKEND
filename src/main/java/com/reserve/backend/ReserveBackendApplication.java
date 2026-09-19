package com.reserve.backend;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootApplication
public class ReserveBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(ReserveBackendApplication.class, args);
	}

	@Bean
	public CommandLineRunner dbMigrationRunner(JdbcTemplate jdbcTemplate) {
		return args -> {
			try {
				jdbcTemplate.execute("ALTER TABLE folders ADD COLUMN IF NOT EXISTS visibility VARCHAR(20) DEFAULT 'PRIVATE';");
				jdbcTemplate.execute("UPDATE folders SET visibility = 'PRIVATE' WHERE visibility IS NULL;");
			} catch (Exception e) {
				System.err.println("Database column migration check: " + e.getMessage());
			}
		};
	}
}
