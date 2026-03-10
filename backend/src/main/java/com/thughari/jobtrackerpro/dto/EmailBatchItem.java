package com.thughari.jobtrackerpro.dto;

import java.time.LocalDateTime;

public record EmailBatchItem(String from, String subject, String replyTo, String body, LocalDateTime receivedDate) {}
