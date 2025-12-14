package com.thughari.jobtrackerpro.config;

import com.thughari.jobtrackerpro.security.JwtAuthenticationFilter;
import com.thughari.jobtrackerpro.security.OAuth2SuccessHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.filter.ForwardedHeaderFilter;

import java.util.List;

@Configuration
public class SecurityConfig {

	private final JwtAuthenticationFilter jwtAuthFilter;
	private final OAuth2SuccessHandler oAuth2SuccessHandler;

	@Value("#{'${app.allowed.cors}'.split(',')}")
	private List<String> allowedCors;

	@Value("#{'${app.allowed.methods}'.split(',')}")
	private List<String> allowedMethods;

	@Value("#{'${app.public.endpoints}'.split(',')}")
	private String[] publicEndpoints;

	public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter, OAuth2SuccessHandler oAuth2SuccessHandler) {
		this.jwtAuthFilter = jwtAuthFilter;
		this.oAuth2SuccessHandler = oAuth2SuccessHandler;
	}

	@Bean
public OAuth2AuthorizationRequestResolver debugResolver(
        ClientRegistrationRepository repo) {

    DefaultOAuth2AuthorizationRequestResolver resolver =
            new DefaultOAuth2AuthorizationRequestResolver(
                    repo, "/oauth2/authorization");

    return new OAuth2AuthorizationRequestResolver() {

        @Override
        public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
            OAuth2AuthorizationRequest authRequest = resolver.resolve(request);
            if (authRequest != null) {
                System.out.println(
                    "OAUTH REDIRECT_URI = " + authRequest.getRedirectUri()
                );
            }
            return authRequest;
        }

        @Override
        public OAuth2AuthorizationRequest resolve(
                HttpServletRequest request, String clientRegistrationId) {

            OAuth2AuthorizationRequest authRequest =
                    resolver.resolve(request, clientRegistrationId);

            if (authRequest != null) {
                System.out.println(
                    "OAUTH REDIRECT_URI = " + authRequest.getRedirectUri()
                );
            }
            return authRequest;
        }
    };
}


	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
		.cors(cors -> cors.configurationSource(request -> {
			var config = new CorsConfiguration();
			config.setAllowedOrigins(allowedCors);
			config.setAllowedMethods(allowedMethods);
			config.setAllowedHeaders(List.of("*"));
			config.setAllowCredentials(true);
			return config;
		}))
		.csrf(csrf -> csrf.disable())
		.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
		.authorizeHttpRequests(auth -> auth
				.requestMatchers(publicEndpoints).permitAll()
				.anyRequest().authenticated()
				)
		.exceptionHandling(e -> e
				.authenticationEntryPoint((request, response, authException) -> {
					response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
				})
				)
		.oauth2Login(oauth2 -> oauth2
				.successHandler(oAuth2SuccessHandler)
				)
		.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
		return config.getAuthenticationManager();
	}
}