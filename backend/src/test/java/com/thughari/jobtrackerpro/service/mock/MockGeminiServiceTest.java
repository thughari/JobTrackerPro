package com.thughari.jobtrackerpro.service.mock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MockGeminiServiceTest {

	@Test
	void buildsMockJobFromEmailAndSubject() {
		MockGeminiService service = new MockGeminiService();

		var result = service.extractJobFromEmail("hr@acme.com", "Backend Engineer", "Body");

		assertEquals("Acme", result.getCompany()); 
		assertEquals("Backend Engineer", result.getRole());
		assertEquals("Applied", result.getStatus());
		assertNotNull(result.getAppliedDate());
	}
}
