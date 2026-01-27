package com.thughari.jobtrackerpro.interfaces;

import com.thughari.jobtrackerpro.dto.JobDTO;

public interface GeminiService {
	
	JobDTO extractJobFromEmail(String from, String subject, String body);

}
