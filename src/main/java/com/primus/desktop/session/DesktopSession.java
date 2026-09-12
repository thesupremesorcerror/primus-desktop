package com.primus.desktop.session;

import com.primus.core.auth.User;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * The desktop replacement for a JWT/HTTP session. There is exactly one of
 * these for the lifetime of the running application (one user, one desktop
 * process) -- it just holds a reference to whoever is currently logged in.
 *
 * AuthService is the only thing that should call setCurrentUser()/clear();
 * everything else (views, controllers) should treat this as read-only.
 */
@Component
public class DesktopSession {

    private User currentUser;

    public void setCurrentUser(User user) {
        this.currentUser = user;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    public String getCurrentUsername() {
        return isLoggedIn() ? currentUser.getUsername() : null;
    }

    public String getCurrentRole() {
        return isLoggedIn() ? currentUser.getRole() : null;
    }

    public void clear() {
        this.currentUser = null;
        SecurityContextHolder.clearContext();
    }
}