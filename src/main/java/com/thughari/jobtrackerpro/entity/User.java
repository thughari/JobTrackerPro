package com.thughari.jobtrackerpro.entity;

import lombok.Data;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Data
@Entity
@Table(name = "users")
public class User {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(unique = true, nullable = false)
	private String email;

	private String name;

	private String password;
	
	@Column(length = 1000)
	private String imageUrl; 

	@Enumerated(EnumType.STRING)
	private AuthProvider provider;
}