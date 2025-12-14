package com.thughari.jobtrackerpro.exception;

public class UserAlreadyExistsException extends RuntimeException {
	
	private static final long serialVersionUID = 3911986493996042236L;

	public UserAlreadyExistsException(String message) {
        super(message);
    }
    
}