package edu.sjsu.cmpe172.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Milestone 3 requirement: a booking still succeeds when the notification service is down.
 * "Down" here is real: the gateway points at a port where nothing listens, so every call
 * gets "connection refused" (no mocks involved).
 */
@TestPropertySource(properties = {
        "notification.enabled=true",
        "notification.base-url=http://127.0.0.1:1/mock-notify",
        "notification.backoff=10ms",
        "notification.redelivery-interval=PT1H",
        "notification.redelivery-initial-delay=PT1H"   // tests call redeliver() themselves
})
class NotificationDownIntegrationTest extends IntegrationTestBase {

    @Autowired
    ObjectMapper json;

    @Autowired
    MeterRegistry metrics;

    private String pendingStatus(long appointmentId, String type) {
        return jdbc.sql("""
                        SELECT status FROM pending_notifications
                         WHERE appointment_id = :id AND type = :type
                        """)
                .param("id", appointmentId).param("type", type)
                .query(String.class).optional().orElse(null);
    }

    @Test
    void bookingAndCancelSucceedWhileNotificationServiceIsDown() throws Exception {
        long slotId = openSlotId("alice.mentor", "Mock Coding Interview");
        double parkedBefore = metrics.counter("scheduler.notifications",
                "result", "parked", "type", "BOOKING_CONFIRMED").count();

        // Book: 201 and the appointment is really in the database.
        String body = mvc.perform(post("/api/customer/appointments").with(as("jordan.dev")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\": %d, \"serviceId\": %d}".formatted(slotId, serviceIdOfSlot(slotId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andReturn().getResponse().getContentAsString();
        long apptId = json.readTree(body).get("id").asLong();
        mvc.perform(get("/api/customer/appointments").with(as("jordan.dev")))
                .andExpect(jsonPath("$[0].id").value(apptId));

        // After the retries fail in the background, the confirmation is parked for redelivery.
        await().atMost(Duration.ofSeconds(10))
                .until(() -> "PENDING".equals(pendingStatus(apptId, "BOOKING_CONFIRMED")));
        assertThat(metrics.counter("scheduler.notifications",
                "result", "parked", "type", "BOOKING_CONFIRMED").count()).isEqualTo(parkedBefore + 1);
        Integer attempts = jdbc.sql("SELECT attempts FROM pending_notifications WHERE appointment_id = :id")
                .param("id", apptId).query(Integer.class).single();
        assertThat(attempts).isEqualTo(3);

        // Cancel works too, and its notification is parked the same way.
        mvc.perform(post("/api/customer/appointments/{id}/cancel", apptId).with(as("jordan.dev")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        await().atMost(Duration.ofSeconds(10))
                .until(() -> "PENDING".equals(pendingStatus(apptId, "BOOKING_CANCELLED")));

        // The app reports itself UP (bookings work) with notifications DEGRADED.
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.database.status").value("UP"))
                .andExpect(jsonPath("$.notifications.status").value("DEGRADED"))
                .andExpect(jsonPath("$.notifications.pending").value(2));
    }
}
