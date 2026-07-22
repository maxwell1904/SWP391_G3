package com.swp391.backend.config;

import com.swp391.backend.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
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
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Vite selects the next port when 5173 is occupied. Allow only local
        // development origins here; deployed environments should set their
        // own gateway/origin policy rather than exposing the API broadly.
        configuration.setAllowedOriginPatterns(List.of("http://localhost:[*]", "http://127.0.0.1:[*]"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // Read-only public catalogue endpoints. Keep mutations below role-protected.
                        .requestMatchers(HttpMethod.GET,
                                "/api/fields",
                                "/api/fields/{fieldId}",
                                "/api/field-types",
                                "/api/slots/search",
                                "/api/slots/suggestions",
                                "/api/services",
                                "/api/promotions",
                                "/api/membership/levels",
                                "/api/uploads/images/*",
                                "/api/payments/paypal/config"
                        ).permitAll()
                        // A visitor may calculate an anonymous basket; booking itself is authenticated.
                        .requestMatchers(HttpMethod.POST,
                                "/api/promotions/apply-preview",
                                "/api/bookings/checkout-preview"
                        ).permitAll()
                        // Public account/system endpoints
                        .requestMatchers(
                                "/api/account/login",
                                "/api/account/register",
                                "/api/account/email/verify",
                                "/api/account/email/resend",
                                "/api/account/forgot-password",
                                "/api/account/reset-password",
                                "/api/account/validate-reset-token",
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
                                "/api/slots/*/unblock",
                                "/api/operations/calendar",
                                "/api/payments/capture"
                        ).hasAnyRole("Staff", "Admin")
                        .requestMatchers(HttpMethod.GET, "/api/refunds").hasAnyRole("Staff", "Admin")
                        .requestMatchers(HttpMethod.PUT, "/api/refunds/**").hasAnyRole("Staff", "Admin")
                        .requestMatchers(HttpMethod.GET, "/api/settings").hasRole("Admin")
                        .requestMatchers(HttpMethod.POST, "/api/promotions").hasRole("Admin")
                        .requestMatchers(HttpMethod.PUT, "/api/promotions/**").hasRole("Admin")
                        // The workflow service enforces that a customer may only cancel
                        // their own booking; Staff/Admin retain the other lifecycle actions.
                        .requestMatchers(HttpMethod.PUT, "/api/bookings/*/services").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/issues/*/status").hasAnyRole("Staff", "Admin")
                        .requestMatchers("/api/account/users/*/activity").hasAnyRole("Staff", "Admin")
                        .requestMatchers(HttpMethod.GET, "/api/account/customers").hasAnyRole("Staff", "Admin")
                        .requestMatchers(HttpMethod.POST, "/api/membership/levels").hasRole("Admin")
                        .requestMatchers(HttpMethod.PUT, "/api/membership/levels/*").hasRole("Admin")
                        .requestMatchers(
                                "/api/account/users",
                                "/api/account/users/*/restriction",
                                "/api/account/users/*/status",
                                "/api/account/staff/**"
                        ).hasRole("Admin")
                        // Authenticated endpoints (Customers, Staff, Admin)
                        .requestMatchers(
                                "/api/bookings/**",
                                "/api/account/users/*/profile",
                                "/api/account/users/*/password",
                                "/api/membership/*/progress",
                                "/api/notifications/**",
                                "/api/payments",
                                "/api/issues"
                        ).authenticated()
                        .anyRequest().authenticated()
                );
        return http.build();
    }
}
