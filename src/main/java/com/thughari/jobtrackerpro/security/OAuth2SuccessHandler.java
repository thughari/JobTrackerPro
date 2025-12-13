package com.thughari.jobtrackerpro.security;

import com.thughari.jobtrackerpro.entity.AuthProvider;
import com.thughari.jobtrackerpro.entity.User;
import com.thughari.jobtrackerpro.repo.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.Map;

@Component
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtUtils jwtUtils;
    
    @Value("${app.ui.url}")
    private String uiUrl;

    public OAuth2SuccessHandler(UserRepository userRepository, JwtUtils jwtUtils) {
        this.userRepository = userRepository;
        this.jwtUtils = jwtUtils;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        OAuth2AuthenticationToken authToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oAuth2User = authToken.getPrincipal();
        
        String registrationId = authToken.getAuthorizedClientRegistrationId();
        
        UserInfo userInfo = extractUserInfo(registrationId, oAuth2User.getAttributes());

        User user = userRepository.findByEmail(userInfo.email).orElseGet(() -> {
            User newUser = new User();
            newUser.setEmail(userInfo.email());
            newUser.setName(userInfo.name());
            newUser.setImageUrl(userInfo.imageUrl());
            newUser.setProvider(AuthProvider.valueOf(registrationId.toUpperCase()));
            return userRepository.save(newUser);
        });

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