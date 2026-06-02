package com.docuinsight.docuinsight;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DocuinsightApplication {

	@Value("${gemini.api.key}")
	private String geminiKey;

	@Value("${jwt.secret}")
	private String jwtSecret;


	@PostConstruct
	public void init() {
		System.out.println("=== Configuration Loaded ===");
		System.out.println("Gemini API Key: " + (geminiKey != null ? geminiKey.substring(0, 5) + "..." : "NOT SET"));
		System.out.println("JWT Secret: " + (jwtSecret != null ? "SET" : "NOT SET"));
		System.out.println("============================");
	}
	public static void main(String[] args) {
		SpringApplication.run(DocuinsightApplication.class, args);
		System.out.println("Hello");



	}

}
