package com.thughari.jobtrackerpro.service.mock;

import org.junit.jupiter.api.Test;
import com.thughari.jobtrackerpro.service.mock.MockAiExtractionService;

import static org.junit.jupiter.api.Assertions.*;

class MockAiExtractionServiceTest {

	@Test
	void buildsMockJobFromEmailAndSubject() {
		MockAiExtractionService service = new MockAiExtractionService();

		var result = service.extractJobFromEmail("hr@acme.com", "Backend Engineer", "Body");

		assertEquals("Acme", result.getCompany()); 
		assertEquals("Backend Engineer", result.getRole());
		assertEquals("Applied", result.getStatus());
		assertNotNull(result.getAppliedDate());
	}
}
