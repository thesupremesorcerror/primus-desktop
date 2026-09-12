package com.primus.desktop;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot entry-point configuration, kept as a plain (non-JavaFX) class.
 *
 * This is intentionally separate from PrimusDesktopApplication (the JavaFX
 * Application subclass). If @SpringBootApplication were placed directly on
 * an Application subclass, Spring would try to instantiate that class as a
 * managed bean via reflection during context startup, which conflicts with
 * the JavaFX lifecycle (Application instances must only be created by the
 * JavaFX launcher). Keeping them separate avoids that entirely.
 *
 * scanBasePackages covers this package and every existing Primus Core
 * package (com.primus.core.*) for ordinary @Component/@Service beans.
 *
 * @EnableJpaRepositories / @EntityScan are required IN ADDITION to
 * scanBasePackages: Spring Boot's automatic JPA repository/entity detection
 * does not use scanBasePackages -- it infers its scan root from the
 * physical package of the @SpringBootApplication class itself
 * (com.primus.desktop), which has no repositories or entities in it. All
 * of Primus Core's repositories (IncidentRepository, UserRepository,
 * KbDocumentRepository, etc.) and entities (Incident, User, KbDocument,
 * etc.) live under com.primus.core, so that has to be named explicitly.
 */
@SpringBootApplication(scanBasePackages = "com.primus")
@EnableJpaRepositories(basePackages = "com.primus.core")
@EntityScan(basePackages = "com.primus.core")
@EnableScheduling
public class PrimusApplicationConfig {
}
