package edu.sjsu.cmpe172.scheduler;

import edu.sjsu.cmpe172.scheduler.exception.ConflictException;
import edu.sjsu.cmpe172.scheduler.exception.ForbiddenException;
import edu.sjsu.cmpe172.scheduler.exception.NotFoundException;
import edu.sjsu.cmpe172.scheduler.model.Appointment;
import edu.sjsu.cmpe172.scheduler.model.AppointmentView;
import edu.sjsu.cmpe172.scheduler.repository.AppointmentRepository;
import edu.sjsu.cmpe172.scheduler.repository.SlotRepository;
import edu.sjsu.cmpe172.scheduler.service.AppointmentService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static edu.sjsu.cmpe172.scheduler.TestData.ALICE;
import static edu.sjsu.cmpe172.scheduler.TestData.CLOCK;
import static edu.sjsu.cmpe172.scheduler.TestData.JORDAN;
import static edu.sjsu.cmpe172.scheduler.TestData.NOW;
import static edu.sjsu.cmpe172.scheduler.TestData.SAM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Owner-only cancel and the upcoming/history scope parameter. */
class AppointmentServiceTest {

    private static final long APPT_ID = 50;
    private static final long SLOT_ID = 10;
    private static final LocalDateTime TOMORROW = NOW.plusDays(1);

    private final AppointmentRepository appointments = mock(AppointmentRepository.class);
    private final SlotRepository slots = mock(SlotRepository.class);
    private final AppointmentService service = new AppointmentService(appointments, slots, CLOCK);

    private void givenAppointment(long customerId, String status, LocalDateTime start) {
        when(appointments.findById(APPT_ID))
                .thenReturn(Optional.of(new Appointment(APPT_ID, SLOT_ID, customerId, status, start)));
    }

    @Test
    void ownerCancelsAndSlotIsReopened() {
        givenAppointment(SAM.id(), Appointment.BOOKED, TOMORROW);
        when(appointments.cancel(APPT_ID)).thenReturn(1);
        when(appointments.findViewById(APPT_ID)).thenReturn(Optional.of(new AppointmentView(
                APPT_ID, SLOT_ID, "Resume Review", "Maria Gonzalez", "Sam Lee",
                TOMORROW, TOMORROW.plusMinutes(30), "CANCELLED", null, NOW, NOW)));

        assertThat(service.cancel(SAM, APPT_ID).status()).isEqualTo("CANCELLED");
        verify(slots).reopen(SLOT_ID);
    }

    @Test
    void someoneElsesAppointmentIsForbidden() {
        givenAppointment(SAM.id(), Appointment.BOOKED, TOMORROW);

        assertThrows(ForbiddenException.class, () -> service.cancel(JORDAN, APPT_ID));
        verify(appointments, never()).cancel(anyLong());
        verify(slots, never()).reopen(anyLong());
    }

    @Test
    void unknownAppointmentIsNotFound() {
        when(appointments.findById(APPT_ID)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.cancel(SAM, APPT_ID));
    }

    @Test
    void alreadyCancelledIsConflict() {
        givenAppointment(SAM.id(), Appointment.CANCELLED, TOMORROW);

        assertThrows(ConflictException.class, () -> service.cancel(SAM, APPT_ID));
    }

    @Test
    void startedAppointmentCannotBeCancelled() {
        givenAppointment(SAM.id(), Appointment.BOOKED, NOW.minusMinutes(5));

        assertThrows(ConflictException.class, () -> service.cancel(SAM, APPT_ID));
    }

    @Test
    void concurrentCancelLosesWithConflict() {
        givenAppointment(SAM.id(), Appointment.BOOKED, TOMORROW);
        when(appointments.cancel(APPT_ID)).thenReturn(0);   // the other request cancelled first

        assertThrows(ConflictException.class, () -> service.cancel(SAM, APPT_ID));
        verify(slots, never()).reopen(anyLong());
    }

    @Test
    void scopeSelectsUpcomingOrHistory() {
        when(appointments.findForCustomer(SAM.id(), true)).thenReturn(List.of());
        when(appointments.findForCustomer(SAM.id(), false)).thenReturn(List.of());

        service.listForCustomer(SAM, "upcoming");
        service.listForCustomer(SAM, "HISTORY");

        verify(appointments).findForCustomer(SAM.id(), true);
        verify(appointments).findForCustomer(SAM.id(), false);
        assertThrows(IllegalArgumentException.class, () -> service.listForCustomer(SAM, "tomorrow"));
    }

    @Test
    void providerListingUsesProviderProfileId() {
        when(appointments.findForProvider(ALICE.providerId(), true)).thenReturn(List.of());

        service.listForProvider(ALICE, "upcoming");

        verify(appointments).findForProvider(ALICE.providerId(), true);
        assertThrows(ForbiddenException.class, () -> service.listForProvider(SAM, "upcoming"));
    }
}
