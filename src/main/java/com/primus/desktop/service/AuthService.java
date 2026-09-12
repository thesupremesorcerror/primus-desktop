package com.primus.desktop.service;

import com.primus.core.auth.User;
import com.primus.core.auth.UserRepository;
import com.primus.desktop.session.DesktopSession;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Desktop replacement for the old AuthController.
 *
 * Old (web):   JavaFX/React -> POST /api/auth/login -> AuthController -> JWT -> Bearer token
 * New (desktop): JavaFX LoginView -> AuthService.login() -> BCrypt verification -> DesktopSession
 *
 * There is no token to hand back and no header to attach to future calls --
 * "being logged in" just means DesktopSession holds the User and
 * SecurityContextHolder holds an Authentication for the current thread, so
 * @PreAuthorize checks on service methods (IncidentService.approveCandidate,
 * etc.) keep working exactly like they did behind the REST layer.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DesktopSession desktopSession;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, DesktopSession desktopSession) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.desktopSession = desktopSession;
    }

    public User register(String username, String rawPassword, String role) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Username already taken: " + username);
        }
        String hashed = passwordEncoder.encode(rawPassword);
        User user = new User(username, hashed, role != null ? role : "VIEWER");
        return userRepository.save(user);
    }

    public User login(String username, String rawPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid username or password");
        }

        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()));
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        desktopSession.setCurrentUser(user);
        return user;
    }

    public void logout() {
        desktopSession.clear();
    }
}
