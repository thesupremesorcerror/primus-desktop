package com.primus.desktop;

import com.primus.core.auth.UserRepository;
import com.primus.desktop.service.AuthService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Desktop-only convenience, not part of Primus Core: there is no
 * /api/auth/register page anymore for the very first login, so if the users
 * table is completely empty this seeds one ADMIN account and prints the
 * password to the console once. Safe to delete once you have a real user
 * (or a Register screen) -- it only ever runs when the table is empty.
 */
@Component
public class DefaultAdminSeeder {

    private static final String DEFAULT_USERNAME = "admin";
    private static final String DEFAULT_PASSWORD = "admin123";

    private final UserRepository userRepository;
    private final AuthService authService;

    public DefaultAdminSeeder(UserRepository userRepository, AuthService authService) {
        this.userRepository = userRepository;
        this.authService = authService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedIfEmpty() {
        if (userRepository.count() == 0) {
            authService.register(DEFAULT_USERNAME, DEFAULT_PASSWORD, "ADMIN");
            System.out.println("======================================================");
            System.out.println(" No users found -- created a default ADMIN account:");
            System.out.println("   username: " + DEFAULT_USERNAME);
            System.out.println("   password: " + DEFAULT_PASSWORD);
            System.out.println(" Log in with these, then create real users and remove");
            System.out.println(" DefaultAdminSeeder once you have a proper user.");
            System.out.println("======================================================");
        }
    }
}