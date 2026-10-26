package com.leaping.portfolio_app.auth.security;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * ECS calls /actuator/health without a token to decide whether this container is ready.
 * It must answer without login and say nothing beyond UP/DOWN, and no other actuator
 * endpoint may be served, since /actuator/env and /actuator/heapdump would expose secrets.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:healthtest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
                "spring.datasource.driverClassName=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.flyway.enabled=false",
                "spring.profiles.active=test",
                "jwt.secret=ThisIsATestOnlyJwtSecretKeyWithAtLeast64CharactersForHS512SigningAlgo",
                // No background jobs, so nothing calls the news/movers feeds during the test
                "leap.scheduling.enabled=false"
        }
)
class HealthEndpointSecurityTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @BeforeEach
    void setUpMockMvc() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void healthAnswersWithoutLoginAndShowsOnlyTheStatus() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void otherActuatorPathsRequireLogin() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/health/db"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void otherActuatorEndpointsAreNotServedEvenToAnAdmin() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/heapdump"))
                .andExpect(status().isNotFound());
    }
}
