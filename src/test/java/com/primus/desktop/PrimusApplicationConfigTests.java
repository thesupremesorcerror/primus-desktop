package com.primus.desktop;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Proves the Spring context boots successfully with no web server involved
 * (WebEnvironment.NONE) -- i.e. WebApplicationType.NONE works end to end
 * with the actual bean graph (JPA, Flyway, agents, security, etc.), same as
 * the old CoreServiceApplicationTests did for the web app before migration.
 */
@SpringBootTest(
        classes = PrimusApplicationConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
class PrimusApplicationConfigTests {

    @Test
    void contextLoads() {
    }
}
