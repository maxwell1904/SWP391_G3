package com.swp391.backend.config;

import com.swp391.backend.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter, UserDetailsService userDetailsService) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers(
                                "/api/account/login",
                                "/api/account/register",
                                "/api/account/email/verify",
                                "/api/account/email/resend",
                                "/api/account/forgot-password",
                                "/api/account/reset-password",
                                "/api/account/validate-reset-token",
                                "/api/fields",
                                "/api/fields/{fieldId}",
                                "/api/field-types",
                                "/api/slots/search",
                                "/api/services",
                                "/api/promotions",
                                "/api/promotions/apply-preview",
                                "/api/bookings/checkout-preview",
                                "/api/membership/levels",
                                "/api/settings",
                                "/api/payments/paypal/config",
                                "/api/system/**",
                                "/api/test",
                                "/api/health",
                                "/h2-console/**"
                        ).permitAll()
                        // Admin-only endpoints
                        .requestMatchers(
                                "/api/admin/**",
                                "/api/reports/**"
                        ).hasRole("Admin")
                        .requestMatchers(HttpMethod.PUT, "/api/settings/**").hasRole("Admin")
                        .requestMatchers(HttpMethod.POST, "/api/settings/**").hasRole("Admin")
                        .requestMatchers(HttpMethod.DELETE, "/api/settings/**").hasRole("Admin")
                        // Staff & Admin endpoints
                        .requestMatchers(
                                "/api/slots/block",
                                "/api/account/users",
                                "/api/account/users/*/restriction",
                                "/api/payments",
                                "/api/refunds"
                        ).hasAnyRole("Staff", "Admin")
                        .requestMatchers(HttpMethod.POST, "/api/promotions").hasAnyRole("Staff", "Admin")
                        .requestMatchers(HttpMethod.PUT, "/api/promotions/**").hasAnyRole("Staff", "Admin")
                        .requestMatchers(HttpMethod.PUT, "/api/bookings/*/status").hasAnyRole("Staff", "Admin")
                        .requestMatchers(HttpMethod.PUT, "/api/issues/*/status").hasAnyRole("Staff", "Admin")
                        // Authenticated endpoints (Customers, Staff, Admin)
                        .requestMatchers(
                                "/api/bookings/**",
                                "/api/account/users/*/profile",
                                "/api/account/users/*/password",
                                "/api/membership/*/progress",
                                "/api/notifications/**",
                                "/api/payments/capture",
                                "/api/issues"
                        ).authenticated()
                        .anyRequest().authenticated()
                );
        return http.build();
    }
}

