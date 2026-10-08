package edu.sjsu.cmpe172.scheduler.service;

import edu.sjsu.cmpe172.scheduler.dto.AppointmentDto;
import edu.sjsu.cmpe172.scheduler.dto.BookingRequest;
import edu.sjsu.cmpe172.scheduler.exception.ForbiddenException;
import edu.sjsu.cmpe172.scheduler.exception.NotFoundException;
import edu.sjsu.cmpe172.scheduler.exception.SlotUnavailableException;
import edu.sjsu.cmpe172.scheduler.model.AppUser;
import edu.sjsu.cmpe172.scheduler.model.Role;
import edu.sjsu.cmpe172.scheduler.repository.AppointmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Books a slot for a customer. Deliberately NOT @Transactional: each attempt runs in
 * its own transaction inside {@link SlotBooker}, so a retry starts from a fresh read
 * instead of reusing a transaction that already lost the race.
 */
@Service
public class BookingService {

    static final int MAX_ATTEMPTS = 3;

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final SlotBooker booker;
    private final AppointmentRepository appointments;

    public BookingService(SlotBooker booker, AppointmentRepository appointments) {
        this.booker = booker;
        this.appointments = appointments;
    }

    public AppointmentDto book(AppUser customer, BookingRequest req) {
        // The URL rule in SecurityConfig already blocks providers; this keeps the rule true
        // even if the service is called from somewhere else.
        if (customer.role() != Role.CUSTOMER) {
            throw new ForbiddenException("Only customers can book appointments");
        }

        for (int attempt = 1; ; attempt++) {
            try {
                long id = booker.bookOnce(customer.id(), req);
                log.info("Customer {} booked slot {} (appointment {}, attempt {})",
                        customer.username(), req.slotId(), id, attempt);
                return appointments.findViewById(id)
                        .map(DtoMapper::toDto)
                        .orElseThrow(() -> new NotFoundException("Appointment " + id + " not found"));
            } catch (OptimisticLockingFailureException e) {
                // Lost a race. Retrying re-reads the slot: if the winner booked it, the next
                // attempt fails fast with 409; if the change was harmless, we can still book.
                log.info("Version conflict booking slot {} (attempt {}/{})", req.slotId(), attempt, MAX_ATTEMPTS);
                if (attempt == MAX_ATTEMPTS) {
                    throw new SlotUnavailableException("Slot " + req.slotId() + " is busy, please try again");
                }
                backOff(attempt);
            } catch (DuplicateKeyException e) {
                // The unique index caught a second active booking for this slot.
                throw new SlotUnavailableException("Slot " + req.slotId() + " is no longer available");
            }
        }
    }

    /** Short randomized pause so two retrying requests do not collide again in lockstep. */
    private static void backOff(int attempt) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(10, 50) * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SlotUnavailableException("Booking interrupted");
        }
    }
}
