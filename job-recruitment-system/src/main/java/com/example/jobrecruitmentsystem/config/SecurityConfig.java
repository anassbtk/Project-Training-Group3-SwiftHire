package com.example.jobrecruitmentsystem.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final AuthenticationSuccessHandler authenticationSuccessHandler;

    public SecurityConfig(AuthenticationSuccessHandler authenticationSuccessHandler) {
        this.authenticationSuccessHandler = authenticationSuccessHandler;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                // Configure resource access rules
                .authorizeHttpRequests(authorize -> authorize
                        // Publicly accessible paths (no authentication/role required)
                        .requestMatchers("/", "/register", "/login", "/css/**", "/js/**", "/images/**", "/about", "/contact",
                                "/api/jobs/suggest", "/forgot-password", "/reset-password",
                                "/employer/logos/download/**", "/seeker/profile/pic/**",

                                // Premium/Checkout URLs
                                "/premium", "/premium/checkout", "/premium/complete").permitAll()

                        // Role-Restricted Access
                        .requestMatchers("/admin/**").hasAuthority("ADMIN")
                        .requestMatchers("/employer/**", "/employer/candidate/**").hasAuthority("EMPLOYER")
                        .requestMatchers("/seeker/**").hasAuthority("JOB_SEEKER")

                        // All other requests must be authenticated
                        .anyRequest().authenticated()
                )
                // Configure form-based login
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler(authenticationSuccessHandler)
                        .permitAll()
                )
                // Configure logout for all roles
                .logout(logout -> logout
                        // Ensure the default POST /logout is used
                        .logoutUrl("/logout")
                        // Explicitly invalidate the HTTP session (FIX)
                        .invalidateHttpSession(true)
                        // Clear the security context (FIX)
                        .clearAuthentication(true)
                        // Remove the remember-me cookie (FIX)
                        .deleteCookies("remember-me")

                        .logoutSuccessUrl("/")
                        .permitAll()
                )
                // Configure "Remember Me" functionality
                .rememberMe(rememberMe -> rememberMe
                        .key("uniqueAndSecureKey")
                        .tokenValiditySeconds(86400 * 30) // 30 days
                )
                // Ensure CSRF is explicitly configured (default behavior is to enable it)
                .csrf(csrf -> {});

        return http.build();
    }
}