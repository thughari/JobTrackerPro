package com.thughari.jobtrackerpro.exception;

public class InvalidImageException extends RuntimeException {

	private static final long serialVersionUID = -6949666812658418320L;

	public InvalidImageException(String message) {
        super(message);
    }

    public InvalidImageException(String message, Throwable cause) {
        super(message, cause);
    }
}
