package com.primus.desktop;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * What survives from the old (deleted) SecurityConfig: BCrypt password
 * hashing and method-level RBAC. What does NOT survive: CORS, the stateless
 * JWT filter chain, and SecurityFilterChain/HttpSecurity -- all of that was
 * HTTP-specific and has no meaning in a desktop app with no servlet
 * requests.
 *
 * @EnableMethodSecurity is what makes @PreAuthorize on service methods
 * (e.g. IncidentService.approveCandidate) work. It only needs a populated
 * SecurityContextHolder -- it does not require a running web server, an
 * HttpServletRequest, or anything else from the web stack. AuthService
 * populates that context at login time (see AuthService.login()).
 */
@Configuration
@EnableMethodSecurity
public class SecurityBeansConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}