package edu.sjsu.cmpe172.scheduler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end through HTTP: Controller -> Service -> Repository -> PostgreSQL and back. */
class BookingFlowIntegrationTest extends IntegrationTestBase {

    @Autowired
    ObjectMapper json;

    private String bookingBody(long slotId) {
        return """
                {"slotId": %d, "serviceId": %d, "notes": "Targeting backend roles"}
                """.formatted(slotId, serviceIdOfSlot(slotId));
    }

    private long book(String username, long slotId) throws Exception {
        String body = mvc.perform(post("/api/customer/appointments").with(as(username)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(bookingBody(slotId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    @Test
    void bookThenSlotIsGoneFromOpenListAndShowsInMyUpcoming() throws Exception {
        long slotId = openSlotId("alice.mentor", "Mock Coding Interview");

        book("jordan.dev", slotId);

        mvc.perform(get("/api/slots").param("size", "50"))
                .andExpect(jsonPath("$.items[*].id", not(hasItem((int) slotId))));
        mvc.perform(get("/api/customer/appointments").with(as("jordan.dev")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slotId").value(slotId))
                .andExpect(jsonPath("$[0].providerName").value("Alice Nguyen"));
    }

    @Test
    void bookingATakenSlotIs409() throws Exception {
        long slotId = openSlotId("alice.mentor", "Mock Coding Interview");
        book("jordan.dev", slotId);

        mvc.perform(post("/api/customer/appointments").with(as("sam.dev")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(bookingBody(slotId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void invalidBookingInputIs400AndUnknownSlotIs404() throws Exception {
        mvc.perform(post("/api/customer/appointments").with(as("sam.dev")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"notes\": \"no slot\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("serviceId: must not be null; slotId: must not be null"));

        mvc.perform(post("/api/customer/appointments").with(as("sam.dev")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/customer/appointments").with(as("sam.dev")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"slotId\": 99999, \"serviceId\": 1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void ownerCancelReopensSlotButOtherCustomerGets403() throws Exception {
        long slotId = openSlotId("raj.mentor", "Mock System Design");
        long apptId = book("jordan.dev", slotId);

        mvc.perform(post("/api/customer/appointments/{id}/cancel", apptId).with(as("sam.dev")).with(csrf()))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/customer/appointments/{id}/cancel", apptId).with(as("jordan.dev")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mvc.perform(get("/api/slots").param("size", "50"))
                .andExpect(jsonPath("$.items[*].id", hasItem((int) slotId)));
        mvc.perform(get("/api/customer/appointments").param("scope", "history").with(as("jordan.dev")))
                .andExpect(jsonPath("$[0].status").value("CANCELLED"));

        // Cancelling twice is a conflict; the freed slot can be booked again.
        mvc.perform(post("/api/customer/appointments/{id}/cancel", apptId).with(as("jordan.dev")).with(csrf()))
                .andExpect(status().isConflict());
        book("sam.dev", slotId);
    }

    @Test
    void providerCreatesListsAndRemovesSlot() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(10).withHour(8).withMinute(0).withSecond(0).withNano(0);
        String body = mvc.perform(post("/api/provider/slots").with(as("alice.mentor")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\": 1, \"startTime\": \"" + start + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn().getResponse().getContentAsString();
        JsonNode created = json.readTree(body);
        long slotId = created.get("id").asLong();

        mvc.perform(get("/api/provider/slots").with(as("alice.mentor")))
                .andExpect(jsonPath("$[*].id", hasItem((int) slotId)));

        // Same start again overlaps: 409. Another provider removing it: 403.
        mvc.perform(post("/api/provider/slots").with(as("alice.mentor")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\": 1, \"startTime\": \"" + start + "\"}"))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/provider/slots/{id}", slotId).with(as("raj.mentor")).with(csrf()))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/provider/slots/{id}", slotId).with(as("alice.mentor")).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/provider/slots").with(as("alice.mentor")))
                .andExpect(jsonPath("$[*].id", not(hasItem((int) slotId))));
    }

    @Test
    void providerCannotCreateSlotInThePast() throws Exception {
        mvc.perform(post("/api/provider/slots").with(as("alice.mentor")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\": 1, \"startTime\": \"2020-01-01T09:00:00\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void providerCannotRemoveBookedSlot() throws Exception {
        long slotId = openSlotId("alice.mentor", "Resume Review");
        book("jordan.dev", slotId);

        mvc.perform(delete("/api/provider/slots/{id}", slotId).with(as("alice.mentor")).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void providerSeesAppointmentsBookedWithThem() throws Exception {
        // Seed data: Sam booked Maria's resume review.
        mvc.perform(get("/api/provider/appointments").with(as("maria.mentor")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].customerName").value("Sam Lee"))
                .andExpect(jsonPath("$[0].status").value("BOOKED"));
        mvc.perform(get("/api/provider/appointments").with(as("alice.mentor")))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void pastBookedAppointmentBecomesCompleted() throws Exception {
        // Move Sam's seeded appointment into the past.
        jdbc.sql("""
                UPDATE availability_slots SET start_time = start_time - INTERVAL '30 days',
                                              end_time = end_time - INTERVAL '30 days'
                 WHERE id IN (SELECT slot_id FROM appointments)
                """).update();

        mvc.perform(get("/api/customer/appointments").param("scope", "history").with(as("sam.dev")))
                .andExpect(jsonPath("$[0].status").value("COMPLETED"));
    }
}
