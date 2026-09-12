package com.primus.desktop;

import javafx.application.Application;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Entry point for the packaged application.
 *
 * This class deliberately does NOT extend javafx.application.Application.
 * When a jar's manifest Main-Class is itself an Application subclass, the
 * JVM's module-boundary check can fail with "JavaFX runtime components are
 * missing" even when the JavaFX jars are correctly on the classpath. Routing
 * through a plain launcher class avoids that check entirely. This is the
 * standard, documented workaround for shipping JavaFX apps as a runnable jar.
 */
public class Launcher {

    public static void main(String[] args) {
        // Spring Security's SecurityContextHolder is ThreadLocal by default,
        // which is designed for one-context-per-HTTP-request web apps: a
        // servlet filter sets it fresh on every request. A desktop app has
        // no such filter. INHERITABLETHREADLOCAL means any thread spawned
        // FROM the thread that logged in (e.g. a background worker started
        // from a JavaFX event handler) automatically inherits the same
        // Authentication, so @PreAuthorize keeps working off the FX thread
        // too. Must be set before Spring or JavaFX start.
        SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_INHERITABLETHREADLOCAL);

        Application.launch(PrimusDesktopApplication.class, args);
    }
}