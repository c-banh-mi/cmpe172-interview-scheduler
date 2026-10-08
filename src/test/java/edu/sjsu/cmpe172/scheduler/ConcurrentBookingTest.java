package edu.sjsu.cmpe172.scheduler;

import edu.sjsu.cmpe172.scheduler.dto.AppointmentDto;
import edu.sjsu.cmpe172.scheduler.dto.BookingRequest;
import edu.sjsu.cmpe172.scheduler.exception.SlotUnavailableException;
import edu.sjsu.cmpe172.scheduler.model.AppUser;
import edu.sjsu.cmpe172.scheduler.model.Slot;
import edu.sjsu.cmpe172.scheduler.repository.SlotRepository;
import edu.sjsu.cmpe172.scheduler.service.BookingService;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The race condition from the report: two customers try to book the same slot at the same time.
 * Runs against a real PostgreSQL so the real locking behavior is tested.
 */
class ConcurrentBookingTest extends IntegrationTestBase {

    @Autowired
    BookingService bookingService;

    @Autowired
    SlotRepository slots;

    @Autowired
    PlatformTransactionManager txManager;

    /** Two threads, one slot, released at the same instant: exactly one booking succeeds. */
    @RepeatedTest(10)
    void twoConcurrentBookingsOfSameSlotExactlyOneSucceeds() throws Exception {
        long slotId = openSlotId("alice.mentor", "Mock Coding Interview");
        BookingRequest req = new BookingRequest(slotId, serviceIdOfSlot(slotId), null);
        AppUser sam = account("sam.dev");
        AppUser jordan = account("jordan.dev");
        int versionBefore = slots.findById(slotId).orElseThrow().version();

        // Both threads wait at the barrier, then call book() at the same moment.
        CyclicBarrier startTogether = new CyclicBarrier(2);
        List<Callable<AppointmentDto>> attempts = List.of(
                () -> { startTogether.await(); return bookingService.book(sam, req); },
                () -> { startTogether.await(); return bookingService.book(jordan, req); });

        int succeeded = 0;
        List<Throwable> failures = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (Future<AppointmentDto> result : pool.invokeAll(attempts, 30, TimeUnit.SECONDS)) {
                try {
                    result.get();
                    succeeded++;
                } catch (ExecutionException e) {
                    failures.add(e.getCause());
                }
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(succeeded).as("successful bookings").isEqualTo(1);
        assertThat(failures).hasSize(1);
        assertThat(failures.get(0)).isInstanceOf(SlotUnavailableException.class);   // -> HTTP 409

        // The database agrees: one active appointment, slot BOOKED, version bumped exactly once.
        long active = jdbc.sql("SELECT COUNT(*) FROM appointments WHERE slot_id = :id AND status = 'BOOKED'")
                .param("id", slotId).query(Long.class).single();
        Slot after = slots.findById(slotId).orElseThrow();
        assertThat(active).isEqualTo(1);
        assertThat(after.status()).isEqualTo(Slot.BOOKED);
        assertThat(after.version()).isEqualTo(versionBefore + 1);
    }

    /**
     * Forces the worst interleaving: both transactions read the slot (same version, OPEN)
     * before either writes. Only one version-checked UPDATE can match.
     */
    @Test
    void bothReadSameVersionButOnlyOneUpdateMatches() throws Exception {
        long slotId = openSlotId("raj.mentor", "Mock System Design");
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        CyclicBarrier bothHaveRead = new CyclicBarrier(2);

        Callable<Integer> attempt = () -> tx.execute(status -> {
            Slot seen = slots.findById(slotId).orElseThrow();
            await(bothHaveRead);   // neither thread writes until both have read version N
            return slots.markBooked(slotId, seen.version());
        });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Integer> rowsUpdated = new ArrayList<>();
        try {
            for (Future<Integer> f : pool.invokeAll(List.of(attempt, attempt), 30, TimeUnit.SECONDS)) {
                rowsUpdated.add(f.get());
            }
        } finally {
            pool.shutdownNow();
        }

        // One UPDATE changed the row; the other waited for its lock, re-checked, and matched nothing.
        assertThat(rowsUpdated).containsExactlyInAnyOrder(1, 0);
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
