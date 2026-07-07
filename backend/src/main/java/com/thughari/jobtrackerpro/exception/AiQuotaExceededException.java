package com.thughari.jobtrackerpro.exception;

public class AiQuotaExceededException extends RuntimeException {
    public AiQuotaExceededException(String message) {
        super(message);
    }
    public AiQuotaExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}
