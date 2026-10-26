package edu.sjsu.cmpe172.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.sjsu.cmpe172.scheduler.mocknotify.MockNotificationStore;
import edu.sjsu.cmpe172.scheduler.notification.NotificationRedeliveryJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
 * The real distribution boundary: the app runs on a random port and its gateway makes real HTTP
 * calls to the mock notification controller, with real timeouts.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "notification.enabled=true",
        "notification.read-timeout=300ms",
        "notification.backoff=10ms",
        "notification.redelivery-interval=PT1H",
        "notification.redelivery-initial-delay=PT1H"   // tests call redeliver() themselves
})
class NotificationIntegrationTest extends IntegrationTestBase {

    @Autowired
    ObjectMapper json;

    @Autowired
    MockNotificationStore mockService;

    @Autowired
    NotificationRedeliveryJob redeliveryJob;

    @BeforeEach
    void resetMockService() {
        // A SLOW-mode request from the previous test may still be sleeping; ids restart with the
        // database, so letting it finish later would store a key this test also uses.
        await().atMost(Duration.ofSeconds(10)).until(() -> mockService.inFlight().get() == 0);
        mockService.clear();
    }

    private long book(String username, long slotId) throws Exception {
        String body = mvc.perform(post("/api/customer/appointments").with(as(username)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\": %d, \"serviceId\": %d}".formatted(slotId, serviceIdOfSlot(slotId))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private long storedWithKey(String key) {
        return mockService.all().stream().filter(s -> s.message().idempotencyKey().equals(key)).count();
    }

    @Test
    void bookAndCancelEachSendOneNotification() throws Exception {
        long apptId = book("jordan.dev", openSlotId("alice.mentor", "Mock Coding Interview"));

        await().atMost(Duration.ofSeconds(10)).until(() -> mockService.all().size() == 1);
        var confirmed = mockService.all().get(0).message();
        assertThat(confirmed.idempotencyKey()).isEqualTo("appointment-" + apptId + "-BOOKING_CONFIRMED");
        assertThat(confirmed.recipient()).isEqualTo("jordan@example.com");
        assertThat(confirmed.type()).isEqualTo("BOOKING_CONFIRMED");
        assertThat(confirmed.message()).contains("Mock Coding Interview", "Alice Nguyen", "confirmed");

        mvc.perform(post("/api/customer/appointments/{id}/cancel", apptId).with(as("jordan.dev")).with(csrf()))
                .andExpect(status().isOk());

        await().atMost(Duration.ofSeconds(10)).until(() -> mockService.all().size() == 2);
        assertThat(mockService.all().get(1).message().type()).isEqualTo("BOOKING_CANCELLED");
        assertThat(jdbc.sql("SELECT COUNT(*) FROM pending_notifications").query(Integer.class).single()).isZero();
    }

    @Test
    void slowServiceTimesOutButBookingIsFastAndRedeliveryRecoversWithoutDuplicates() throws Exception {
        // The service takes 1 s to answer; the gateway gives up after 300 ms.
        mockService.setMode(MockNotificationStore.Mode.SLOW, Duration.ofSeconds(1));

        long started = System.nanoTime();
        long apptId = book("sam.dev", openSlotId("raj.mentor", "Mock System Design"));
        long bookingMs = (System.nanoTime() - started) / 1_000_000;
        // Commit-then-notify on a background thread: the customer does not wait for the timeouts.
        assertThat(bookingMs).isLessThan(1000);

        String key = "appointment-" + apptId + "-BOOKING_CONFIRMED";
        await().atMost(Duration.ofSeconds(10)).until(() -> "PENDING".equals(
                jdbc.sql("SELECT status FROM pending_notifications WHERE idempotency_key = :k")
                        .param("k", key).query(String.class).optional().orElse(null)));

        // The slow service did process the timed-out requests eventually...
        await().atMost(Duration.ofSeconds(10)).until(() -> storedWithKey(key) == 1);

        // ...so when it is healthy again, redelivery is answered DUPLICATE, not stored twice.
        mockService.setMode(MockNotificationStore.Mode.UP, null);
        redeliveryJob.redeliver();

        assertThat(jdbc.sql("SELECT status FROM pending_notifications WHERE idempotency_key = :k")
                .param("k", key).query(String.class).single()).isEqualTo("SENT");
        assertThat(storedWithKey(key)).isEqualTo(1);
        assertThat(mockService.requestCount()).isEqualTo(4);   // 3 timed-out attempts + 1 redelivery

        mvc.perform(get("/health")).andExpect(jsonPath("$.notifications.status").value("UP"));
    }

    @Test
    void serviceAnswering503IsParkedThenRedeliveredWhenBack() throws Exception {
        mockService.setMode(MockNotificationStore.Mode.DOWN, null);
        long apptId = book("jordan.dev", openSlotId("maria.mentor", "Resume Review"));
        String key = "appointment-" + apptId + "-BOOKING_CONFIRMED";

        await().atMost(Duration.ofSeconds(10)).until(() ->
                jdbc.sql("SELECT COUNT(*) FROM pending_notifications WHERE status = 'PENDING'")
                        .query(Integer.class).single() == 1);
        assertThat(storedWithKey(key)).isZero();

        // Still down: the job keeps it PENDING.
        redeliveryJob.redeliver();
        assertThat(storedWithKey(key)).isZero();

        mockService.setMode(MockNotificationStore.Mode.UP, null);
        redeliveryJob.redeliver();
        assertThat(storedWithKey(key)).isEqualTo(1);
        assertThat(jdbc.sql("SELECT attempts FROM pending_notifications WHERE idempotency_key = :k")
                .param("k", key).query(Integer.class).single()).isEqualTo(5);  // 3 + 1 + 1
    }

    @Test
    void healthEndpointReportsDatabase() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.database.status").value("UP"))
                .andExpect(jsonPath("$.database.responseTimeMs").isNumber());
    }

    @Test
    void bookingMetricIsExposed() throws Exception {
        book("jordan.dev", openSlotId("alice.mentor", "Resume Review"));

        mvc.perform(get("/actuator/metrics/scheduler.booking").param("tag", "outcome:booked"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measurements[?(@.statistic == 'COUNT')].value").isNotEmpty());
    }
}
