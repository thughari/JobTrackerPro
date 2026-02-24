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
	
	@Column(name = "gmail_refresh_token")
	private String gmailRefreshToken;
	
	@Column(name = "gmail_history_id")
	private String gmailHistoryId;

	@Column(name = "gmail_watch_expiration")
	private Long gmailWatchExpiration;

	@Column(name = "gmail_sync_in_progress")
	private Boolean gmailSyncInProgress = false;

	@Column(name = "gmail_connected")
	private Boolean gmailConnected = false;
	
	@Column(name = "gmail_label_id")
	private String gmailLabelId;
}