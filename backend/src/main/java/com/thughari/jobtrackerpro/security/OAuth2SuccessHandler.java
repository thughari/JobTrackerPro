package com.thughari.jobtrackerpro.security;

import com.thughari.jobtrackerpro.entity.AuthProvider;
import com.thughari.jobtrackerpro.entity.User;
import com.thughari.jobtrackerpro.repo.UserRepository;
import com.thughari.jobtrackerpro.service.StorageService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.Map;

@Component
@Slf4j
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

	private final UserRepository userRepository;
	private final JwtUtils jwtUtils;
	private final StorageService storageService;

	@Value("${app.ui.url}")
	private String uiUrl;

	public OAuth2SuccessHandler(UserRepository userRepository, JwtUtils jwtUtils, StorageService storageService) {
		this.userRepository = userRepository;
		this.jwtUtils = jwtUtils;
		this.storageService = storageService;
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
		OAuth2AuthenticationToken authToken = (OAuth2AuthenticationToken) authentication;
		OAuth2User oAuth2User = authToken.getPrincipal();

		String registrationId = authToken.getAuthorizedClientRegistrationId();
		UserInfo userInfo = extractUserInfo(registrationId, oAuth2User.getAttributes());

		User user = userRepository.findByEmail(userInfo.email).orElse(new User());

		boolean isNewUser = user.getId() == null;
		boolean dataChanged = false;

		if (isNewUser) {
			user.setEmail(userInfo.email());
			user.setProvider(AuthProvider.valueOf(registrationId.toUpperCase()));
			user = userRepository.save(user); 
		}

		if (user.getName() == null || !user.getName().equals(userInfo.name())) {
			user.setName(userInfo.name());
			dataChanged = true;
		}

		if (user.getImageUrl() == null || user.getImageUrl().isEmpty()) {
			if (userInfo.imageUrl() != null && !userInfo.imageUrl().isEmpty()) {
				try {
					String r2Url = storageService.uploadFromUrl(userInfo.imageUrl(), user.getId().toString());
					user.setImageUrl(r2Url);
					dataChanged = true;
				} catch (Exception e) {
					log.error("Failed to sync social image: " + e.getMessage());
				}
			}
		}

		if (dataChanged) {
			userRepository.save(user);
		}

		String token = jwtUtils.generateToken(user.getEmail());
		getRedirectStrategy().sendRedirect(request, response, uiUrl + "/login-success?token=" + token);
	}

	private UserInfo extractUserInfo(String provider, Map<String, Object> attributes) {
		String email = "";
		String name = "";
		String imageUrl = "";

		switch (provider.toLowerCase()) {
		case "google":
			email = (String) attributes.get("email");
			name = (String) attributes.get("name");
			imageUrl = (String) attributes.get("picture");
			break;

		case "github":
			email = (String) attributes.get("email");
			name = (String) attributes.get("name");
			imageUrl = (String) attributes.get("avatar_url");
			if (email == null) {
				email = attributes.get("login") + "@github.com"; 
			}
			if (name == null) {
				name = (String) attributes.get("login");
			}
			break;
		}
		return new UserInfo(email, name, imageUrl);
	}

	record UserInfo(String email, String name, String imageUrl) {}
}