package com.thughari.jobtrackerpro.dto;

import lombok.Data;

@Data
public class AuthRequest {
	private String email;
	private String password;
	private String name;
}
