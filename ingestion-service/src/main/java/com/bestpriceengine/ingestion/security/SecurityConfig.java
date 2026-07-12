package com.bestpriceengine.ingestion.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(AppUserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    // Session-cookie auth (not JWT) -- simpler through the single-origin nginx setup this app
    // already uses, no token storage/refresh needed on the frontend.
    //
    // CSRF is deliberately disabled: this is a same-origin demo app with no third-party embed
    // surface, and the session cookie carries no elevated privilege beyond "which demo user is
    // this" -- a real production deployment handling real payment/PII would need CSRF protection
    // (or a double-submit token) back on.
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login", "/api/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/me").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/offers", "/api/products").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/offers").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/offers/*/price").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/bulk-pricing-requests").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/price-watch-orders/*").permitAll()
                        .requestMatchers("/api/orders/**").authenticated()
                        .requestMatchers("/api/price-watch-orders/**").authenticated()
                        .anyRequest().permitAll()
                )
                .exceptionHandling(eh -> eh.authenticationEntryPoint(
                        (request, response, authException) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "authentication required")
                ));
        return http.build();
    }
}
