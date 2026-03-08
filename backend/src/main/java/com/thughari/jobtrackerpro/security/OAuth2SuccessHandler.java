package com.thughari.jobtrackerpro.security;

import com.thughari.jobtrackerpro.entity.User;
import com.thughari.jobtrackerpro.service.AuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
@Slf4j
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

	private final JwtUtils jwtUtils;
	private final AuthService authService;

	@Value("${app.ui.url}")
	private String uiUrl;

	@Value("${app.jwt.refresh-expiration-ms}")
	private long refreshExpirationMs;

	@Value("${app.jwt.refresh-cookie-secure}")
	private boolean refreshCookieSecure;

	@Value("${app.jwt.refresh-cookie-same-site:Lax}")
	private String refreshCookieSameSite;

	public OAuth2SuccessHandler(JwtUtils jwtUtils, AuthService authService) {
		this.jwtUtils = jwtUtils;
		this.authService = authService;
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
	    OAuth2AuthenticationToken authToken = (OAuth2AuthenticationToken) authentication;
	    String registrationId = authToken.getAuthorizedClientRegistrationId();
	    UserInfo userInfo = extractUserInfo(registrationId, authToken.getPrincipal().getAttributes());

	    User user = authService.processOAuthUser(userInfo.email(), userInfo.name(), userInfo.imageUrl(), registrationId);

	    String token = jwtUtils.generateAccessToken(user.getEmail());
	    String refreshToken = jwtUtils.generateRefreshToken(user.getEmail());
	    
	    response.addHeader("Set-Cookie", buildRefreshCookie(refreshToken, "/", refreshExpirationMs / 1000).toString());
	    
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

	private ResponseCookie buildRefreshCookie(String value, String path, long maxAgeSeconds) {
		return ResponseCookie.from("refresh_token", value)
				.httpOnly(true)
				.secure(refreshCookieSecure)
				.path(path)
				.sameSite(refreshCookieSameSite)
				.maxAge(maxAgeSeconds)
				.build();
	}

	record UserInfo(String email, String name, String imageUrl) {}
}
