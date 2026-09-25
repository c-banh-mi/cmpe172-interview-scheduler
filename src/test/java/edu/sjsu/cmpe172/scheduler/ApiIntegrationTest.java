package edu.sjsu.cmpe172.scheduler;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class ApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Test
    void homeReturnsCatalogFromSeedData() throws Exception {
        mvc.perform(get("/api/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.services.length()").value(4))
                .andExpect(jsonPath("$.providers.length()").value(3))
                // 10 seeded slots, 1 already booked
                .andExpect(jsonPath("$.openSlotCount").value(9));
    }

    @Test
    void slotsArePaginatedWithLimitOffset() throws Exception {
        mvc.perform(get("/api/slots").param("page", "0").param("size", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(4))
                .andExpect(jsonPath("$.totalItems").value(9))
                .andExpect(jsonPath("$.totalPages").value(3));

        mvc.perform(get("/api/slots").param("page", "2").param("size", "4"))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void slotsCanBeFilteredByServiceAndDate() throws Exception {
        long resumeReviewId = jdbc.sql("SELECT id FROM services WHERE name = 'Resume Review'")
                .query(Long.class).single();
        // Ask the DB for "tomorrow" so the test agrees with how seed.sql computed slot times.
        String tomorrow = jdbc.sql("SELECT CURRENT_DATE + 1").query(LocalDate.class).single().toString();

        mvc.perform(get("/api/slots")
                        .param("serviceId", String.valueOf(resumeReviewId))
                        .param("date", tomorrow))
                .andExpect(status().isOk())
                // Maria's 09:00 is booked, so only her 09:30 remains
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[*].serviceName", everyItem(is("Resume Review"))))
                .andExpect(jsonPath("$.items[*].startTime", everyItem(startsWith(tomorrow))));
    }

    @Test
    void responsesAreDtosNotRows() throws Exception {
        mvc.perform(get("/api/slots"))
                .andExpect(jsonPath("$.items[0].version").doesNotExist())
                .andExpect(jsonPath("$.items[0].status").doesNotExist());
    }

    @Test
    void invalidPagingIsBadRequest() throws Exception {
        mvc.perform(get("/api/slots").param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        mvc.perform(get("/api/slots").param("date", "not-a-date"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void secondActiveBookingForSameSlotIsRejectedByDatabase() {
        long bookedSlot = jdbc.sql("SELECT slot_id FROM appointments WHERE status = 'BOOKED' LIMIT 1")
                .query(Long.class).single();
        long jordan = jdbc.sql("SELECT id FROM users WHERE username = 'jordan.dev'").query(Long.class).single();

        assertThrows(org.springframework.dao.DuplicateKeyException.class, () -> jdbc.sql("""
                        INSERT INTO appointments (slot_id, service_id, customer_id)
                        SELECT id, service_id, :customer FROM availability_slots WHERE id = :slot
                        """)
                .param("customer", jordan)
                .param("slot", bookedSlot)
                .update());
    }
}
