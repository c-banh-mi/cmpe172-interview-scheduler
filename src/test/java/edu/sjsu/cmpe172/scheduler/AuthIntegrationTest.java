package edu.sjsu.cmpe172.scheduler;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Session login, BCrypt storage, and role-based access (401 vs 403). */
class AuthIntegrationTest extends IntegrationTestBase {

    private MockHttpSession login(String username, String password) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf())
                        .param("username", username).param("password", password))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }

    @Test
    void passwordsAreStoredAsBcryptHashes() {
        String hash = jdbc.sql("SELECT password_hash FROM users WHERE username = 'sam.dev'")
                .query(String.class).single();
        assertThat(hash).startsWith("$2a$10$").isNotEqualTo("password123");
    }

    @Test
    void loginCreatesSessionAndReturnsRoleFromDatabase() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf())
                        .param("username", "alice.mentor").param("password", "password123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice.mentor"))
                .andExpect(jsonPath("$.role").value("PROVIDER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        MockHttpSession session = login("sam.dev", "password123");
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
        mvc.perform(get("/api/customer/appointments").session(session))
                .andExpect(status().isOk());
    }

    @Test
    void wrongPasswordIs401() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf())
                        .param("username", "sam.dev").param("password", "wrong"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        MockHttpSession session = login("sam.dev", "password123");
        mvc.perform(post("/api/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/customer/appointments").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousUserIs401OnProtectedEndpoints() throws Exception {
        mvc.perform(get("/api/customer/appointments"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        mvc.perform(get("/api/provider/slots"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isNoContent());
    }

    @Test
    void customerOnProviderEndpointIs403() throws Exception {
        mvc.perform(get("/api/provider/slots").with(as("sam.dev")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        mvc.perform(post("/api/provider/slots").with(as("sam.dev")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\": 1, \"startTime\": \"2030-01-01T09:00:00\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void providerOnCustomerEndpointIs403() throws Exception {
        long slotId = openSlotId("raj.mentor", "Mock System Design");
        mvc.perform(post("/api/customer/appointments").with(as("alice.mentor")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\": " + slotId + ", \"serviceId\": " + serviceIdOfSlot(slotId) + "}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void writesWithoutCsrfTokenAreRejected() throws Exception {
        mvc.perform(post("/api/customer/appointments/1/cancel").with(as("sam.dev")))
                .andExpect(status().isForbidden());
    }

    @Test
    void publicEndpointsNeedNoLogin() throws Exception {
        mvc.perform(get("/api/slots")).andExpect(status().isOk());
        mvc.perform(get("/api/home")).andExpect(status().isOk());
        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
