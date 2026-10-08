package edu.sjsu.cmpe172.scheduler;

import edu.sjsu.cmpe172.scheduler.dto.AppointmentDto;
import edu.sjsu.cmpe172.scheduler.dto.BookingRequest;
import edu.sjsu.cmpe172.scheduler.exception.ConflictException;
import edu.sjsu.cmpe172.scheduler.exception.ForbiddenException;
import edu.sjsu.cmpe172.scheduler.exception.NotFoundException;
import edu.sjsu.cmpe172.scheduler.exception.SlotUnavailableException;
import edu.sjsu.cmpe172.scheduler.model.AppointmentView;
import edu.sjsu.cmpe172.scheduler.model.Slot;
import edu.sjsu.cmpe172.scheduler.repository.AppointmentRepository;
import edu.sjsu.cmpe172.scheduler.repository.SlotRepository;
import edu.sjsu.cmpe172.scheduler.service.BookingService;
import edu.sjsu.cmpe172.scheduler.service.SlotBooker;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.Optional;

import static edu.sjsu.cmpe172.scheduler.TestData.ALICE;
import static edu.sjsu.cmpe172.scheduler.TestData.CLOCK;
import static edu.sjsu.cmpe172.scheduler.TestData.NOW;
import static edu.sjsu.cmpe172.scheduler.TestData.SAM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Booking rules (SlotBooker) and the retry loop (BookingService), with mocked repositories. */
class BookingServiceTest {

    private static final long SLOT_ID = 10;
    private static final long SERVICE_ID = 2;
    private static final LocalDateTime TOMORROW_10 = NOW.plusDays(1).withHour(10);

    private final SlotRepository slots = mock(SlotRepository.class);
    private final AppointmentRepository appointments = mock(AppointmentRepository.class);
    private final BookingService service =
            new BookingService(new SlotBooker(slots, appointments, CLOCK), appointments);

    private final BookingRequest request = new BookingRequest(SLOT_ID, SERVICE_ID, "new-grad backend");

    private static Slot slot(String status, int version, LocalDateTime start) {
        return new Slot(SLOT_ID, 1, SERVICE_ID, start, start.plusHours(1), status, version);
    }

    private void givenSlot(Slot slot) {
        when(slots.findById(SLOT_ID)).thenReturn(Optional.of(slot));
    }

    private void givenAppointmentView(long id) {
        when(appointments.findViewById(id)).thenReturn(Optional.of(new AppointmentView(
                id, SLOT_ID, "Mock Coding Interview", "Alice Nguyen", "Sam Lee",
                TOMORROW_10, TOMORROW_10.plusHours(1), "BOOKED", "new-grad backend", NOW, null)));
    }

    @Test
    void booksOpenFutureSlotWithVersionCheck() {
        givenSlot(slot(Slot.OPEN, 3, TOMORROW_10));
        when(slots.markBooked(SLOT_ID, 3)).thenReturn(1);
        when(appointments.insert(SLOT_ID, SERVICE_ID, SAM.id(), "new-grad backend")).thenReturn(99L);
        givenAppointmentView(99);

        AppointmentDto booked = service.book(SAM, request);

        assertThat(booked.id()).isEqualTo(99);
        assertThat(booked.status()).isEqualTo("BOOKED");
        verify(slots).markBooked(SLOT_ID, 3);   // the version we read is the version we require
    }

    @Test
    void rejectsSlotThatIsAlreadyBooked() {
        givenSlot(slot(Slot.BOOKED, 4, TOMORROW_10));

        assertThrows(SlotUnavailableException.class, () -> service.book(SAM, request));
        verify(slots, never()).markBooked(anyLong(), anyInt());
        verify(appointments, never()).insert(anyLong(), anyLong(), anyLong(), any());
    }

    @Test
    void rejectsSlotInThePast() {
        givenSlot(slot(Slot.OPEN, 0, NOW.minusHours(1)));

        assertThrows(SlotUnavailableException.class, () -> service.book(SAM, request));
    }

    @Test
    void rejectsUnknownSlot() {
        when(slots.findById(SLOT_ID)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.book(SAM, request));
    }

    @Test
    void rejectsServiceThatDoesNotMatchTheSlot() {
        givenSlot(slot(Slot.OPEN, 0, TOMORROW_10));

        assertThrows(IllegalArgumentException.class,
                () -> service.book(SAM, new BookingRequest(SLOT_ID, SERVICE_ID + 1, null)));
    }

    @Test
    void rejectsOverlappingAppointmentForSameCustomer() {
        givenSlot(slot(Slot.OPEN, 0, TOMORROW_10));
        when(appointments.customerHasOverlap(SAM.id(), TOMORROW_10, TOMORROW_10.plusHours(1))).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.book(SAM, request));
        verify(slots, never()).markBooked(anyLong(), anyInt());
    }

    @Test
    void providersCannotBook() {
        assertThrows(ForbiddenException.class, () -> service.book(ALICE, request));
        verify(slots, never()).findById(anyLong());
    }

    @Test
    void retriesAfterVersionConflictAndThenSeesSlotTaken() {
        // Attempt 1 reads version 3, but another transaction books first: our UPDATE matches 0 rows.
        // Attempt 2 re-reads and finds the slot BOOKED: 409, no more retries.
        when(slots.findById(SLOT_ID)).thenReturn(
                Optional.of(slot(Slot.OPEN, 3, TOMORROW_10)),
                Optional.of(slot(Slot.BOOKED, 4, TOMORROW_10)));
        when(slots.markBooked(SLOT_ID, 3)).thenReturn(0);

        assertThrows(SlotUnavailableException.class, () -> service.book(SAM, request));
        verify(slots, times(2)).findById(SLOT_ID);
        verify(appointments, never()).insert(anyLong(), anyLong(), anyLong(), any());
    }

    @Test
    void retrySucceedsWhenSlotIsStillOpenWithNewVersion() {
        // The version moved (e.g. booked then cancelled) but the slot is OPEN again: attempt 2 wins.
        when(slots.findById(SLOT_ID)).thenReturn(
                Optional.of(slot(Slot.OPEN, 3, TOMORROW_10)),
                Optional.of(slot(Slot.OPEN, 5, TOMORROW_10)));
        when(slots.markBooked(SLOT_ID, 3)).thenReturn(0);
        when(slots.markBooked(SLOT_ID, 5)).thenReturn(1);
        when(appointments.insert(SLOT_ID, SERVICE_ID, SAM.id(), "new-grad backend")).thenReturn(7L);
        givenAppointmentView(7);

        assertThat(service.book(SAM, request).id()).isEqualTo(7);
    }

    @Test
    void givesUpAfterMaxAttempts() {
        givenSlot(slot(Slot.OPEN, 3, TOMORROW_10));
        when(slots.markBooked(SLOT_ID, 3)).thenReturn(0);

        assertThrows(SlotUnavailableException.class, () -> service.book(SAM, request));
        verify(slots, times(3)).markBooked(SLOT_ID, 3);
    }

    @Test
    void uniqueIndexViolationBecomesSlotUnavailable() {
        givenSlot(slot(Slot.OPEN, 3, TOMORROW_10));
        when(slots.markBooked(SLOT_ID, 3)).thenReturn(1);
        when(appointments.insert(anyLong(), anyLong(), anyLong(), any()))
                .thenThrow(new DuplicateKeyException("uq_appt_active_slot"));

        assertThrows(SlotUnavailableException.class, () -> service.book(SAM, request));
    }
}
