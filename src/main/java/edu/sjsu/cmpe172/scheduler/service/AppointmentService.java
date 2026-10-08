package edu.sjsu.cmpe172.scheduler.service;

import edu.sjsu.cmpe172.scheduler.dto.AppointmentDto;
import edu.sjsu.cmpe172.scheduler.exception.ConflictException;
import edu.sjsu.cmpe172.scheduler.exception.ForbiddenException;
import edu.sjsu.cmpe172.scheduler.exception.NotFoundException;
import edu.sjsu.cmpe172.scheduler.model.AppUser;
import edu.sjsu.cmpe172.scheduler.model.Appointment;
import edu.sjsu.cmpe172.scheduler.repository.AppointmentRepository;
import edu.sjsu.cmpe172.scheduler.repository.SlotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** Listing and cancelling appointments. */
@Service
public class AppointmentService {

    private final AppointmentRepository appointments;
    private final SlotRepository slots;
    private final Clock clock;

    public AppointmentService(AppointmentRepository appointments, SlotRepository slots, Clock clock) {
        this.appointments = appointments;
        this.slots = slots;
        this.clock = clock;
    }

    /** scope is "upcoming" or "history". */
    @Transactional(readOnly = true)
    public List<AppointmentDto> listForCustomer(AppUser customer, String scope) {
        return appointments.findForCustomer(customer.id(), isUpcoming(scope)).stream()
                .map(DtoMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AppointmentDto> listForProvider(AppUser provider, String scope) {
        if (provider.providerId() == null) {
            throw new ForbiddenException("Only providers have a booking calendar");
        }
        return appointments.findForProvider(provider.providerId(), isUpcoming(scope)).stream()
                .map(DtoMapper::toDto)
                .toList();
    }

    /**
     * Owner-only cancel. Marks the appointment CANCELLED and reopens the slot in the same
     * transaction, so the slot is never left BOOKED with no active appointment (or vice versa).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentDto cancel(AppUser customer, long appointmentId) {
        Appointment appt = appointments.findById(appointmentId)
                .orElseThrow(() -> new NotFoundException("Appointment " + appointmentId + " not found"));

        if (appt.customerId() != customer.id()) {
            throw new ForbiddenException("You can only cancel your own appointments");
        }
        if (!Appointment.BOOKED.equals(appt.status())) {
            throw new ConflictException("Appointment " + appointmentId + " is already " + appt.status());
        }
        if (!appt.startTime().isAfter(LocalDateTime.now(clock))) {
            throw new ConflictException("Appointments that have already started cannot be cancelled");
        }
        // Conditional update: if two cancel requests race, only one changes the row.
        if (appointments.cancel(appointmentId) == 0) {
            throw new ConflictException("Appointment " + appointmentId + " was already cancelled");
        }
        slots.reopen(appt.slotId());

        return appointments.findViewById(appointmentId)
                .map(DtoMapper::toDto)
                .orElseThrow(() -> new NotFoundException("Appointment " + appointmentId + " not found"));
    }

    static boolean isUpcoming(String scope) {
        if (scope == null || scope.equalsIgnoreCase("upcoming")) {
            return true;
        }
        if (scope.equalsIgnoreCase("history")) {
            return false;
        }
        throw new IllegalArgumentException("scope must be 'upcoming' or 'history'");
    }
}
