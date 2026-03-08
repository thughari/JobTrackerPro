package com.thughari.jobtrackerpro.interfaces;

import java.util.List;

import com.thughari.jobtrackerpro.dto.EmailBatchItem;
import com.thughari.jobtrackerpro.dto.JobDTO;

public interface GeminiService {
	
	JobDTO extractJobFromEmail(String from, String subject, String body);
	
	List<JobDTO> extractJobsFromBatch(List<EmailBatchItem> items);

}
