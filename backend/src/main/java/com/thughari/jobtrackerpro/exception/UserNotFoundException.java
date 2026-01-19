package com.thughari.jobtrackerpro.exception;

public class UserNotFoundException extends RuntimeException {

	private static final long serialVersionUID = -6197123101871737275L;
	
	public UserNotFoundException(String message) {
		super(message);
	}

}
