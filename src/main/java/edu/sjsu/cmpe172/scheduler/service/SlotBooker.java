package edu.sjsu.cmpe172.scheduler.service;

import edu.sjsu.cmpe172.scheduler.dto.BookingRequest;
import edu.sjsu.cmpe172.scheduler.exception.ConflictException;
import edu.sjsu.cmpe172.scheduler.exception.NotFoundException;
import edu.sjsu.cmpe172.scheduler.exception.SlotUnavailableException;
import edu.sjsu.cmpe172.scheduler.model.Slot;
import edu.sjsu.cmpe172.scheduler.repository.AppointmentRepository;
import edu.sjsu.cmpe172.scheduler.repository.SlotRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * One booking attempt, as one database transaction. {@link BookingService} calls this
 * in a retry loop; it lives in its own bean so every call goes through Spring's
 * transactional proxy and gets a fresh transaction.
 */
@Component
public class SlotBooker {

    private final SlotRepository slots;
    private final AppointmentRepository appointments;
    private final Clock clock;

    public SlotBooker(SlotRepository slots, AppointmentRepository appointments, Clock clock) {
        this.slots = slots;
        this.appointments = appointments;
        this.clock = clock;
    }

    /**
     * Read the slot, check the booking rules, then claim it with a version-checked UPDATE
     * and insert the appointment. Both writes commit together or not at all.
     *
     * @return the new appointment's id
     * @throws OptimisticLockingFailureException if another transaction changed the slot
     *         between our read and our update (the caller retries)
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public long bookOnce(long customerId, BookingRequest req) {
        Slot slot = slots.findById(req.slotId())
                .orElseThrow(() -> new NotFoundException("Slot " + req.slotId() + " not found"));

        if (slot.serviceId() != req.serviceId()) {
            throw new IllegalArgumentException("Slot " + slot.id() + " is not offered for service " + req.serviceId());
        }
        if (!Slot.OPEN.equals(slot.status())) {
            throw new SlotUnavailableException("Slot " + slot.id() + " is no longer available");
        }
        if (!slot.startTime().isAfter(LocalDateTime.now(clock))) {
            throw new SlotUnavailableException("Slot " + slot.id() + " has already started");
        }
        if (appointments.customerHasOverlap(customerId, slot.startTime(), slot.endTime())) {
            throw new ConflictException("You already have an appointment at that time");
        }

        // The concurrency check: succeeds only if the row still has the version we read above.
        if (slots.markBooked(slot.id(), slot.version()) == 0) {
            throw new OptimisticLockingFailureException("Slot " + slot.id() + " changed while booking");
        }
        // Backstop: the partial unique index uq_appt_active_slot rejects a second BOOKED row
        // for this slot even if the check above were ever bypassed (DuplicateKeyException).
        return appointments.insert(slot.id(), slot.serviceId(), customerId, req.notes());
    }
}
