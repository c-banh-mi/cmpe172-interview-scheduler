package edu.sjsu.cmpe172.scheduler;

import edu.sjsu.cmpe172.scheduler.dto.CreateSlotRequest;
import edu.sjsu.cmpe172.scheduler.exception.ConflictException;
import edu.sjsu.cmpe172.scheduler.exception.ForbiddenException;
import edu.sjsu.cmpe172.scheduler.exception.NotFoundException;
import edu.sjsu.cmpe172.scheduler.model.ServiceOffering;
import edu.sjsu.cmpe172.scheduler.model.Slot;
import edu.sjsu.cmpe172.scheduler.model.SlotView;
import edu.sjsu.cmpe172.scheduler.repository.ServiceOfferingRepository;
import edu.sjsu.cmpe172.scheduler.repository.SlotRepository;
import edu.sjsu.cmpe172.scheduler.service.ProviderSlotService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static edu.sjsu.cmpe172.scheduler.TestData.ALICE;
import static edu.sjsu.cmpe172.scheduler.TestData.CLOCK;
import static edu.sjsu.cmpe172.scheduler.TestData.NOW;
import static edu.sjsu.cmpe172.scheduler.TestData.RAJ;
import static edu.sjsu.cmpe172.scheduler.TestData.SAM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Provider availability rules: slot length, overlap, ownership, and booked-slot protection. */
class ProviderSlotServiceTest {

    private static final ServiceOffering CODING = new ServiceOffering(2, "Mock Coding Interview", "", 60, 5000);
    private static final LocalDateTime START = NOW.plusDays(2).withHour(9);

    private final SlotRepository slots = mock(SlotRepository.class);
    private final ServiceOfferingRepository services = mock(ServiceOfferingRepository.class);
    private final ProviderSlotService service = new ProviderSlotService(slots, services, CLOCK);

    @Test
    void createdSlotLastsAsLongAsTheService() {
        when(services.findById(2)).thenReturn(Optional.of(CODING));
        when(slots.insert(1, 2, START, START.plusMinutes(60))).thenReturn(30L);
        when(slots.findViewById(30)).thenReturn(Optional.of(new SlotView(
                30, 1, "Alice Nguyen", 2, "Mock Coding Interview", 60, 5000, START, START.plusMinutes(60), "OPEN")));

        var created = service.create(ALICE, new CreateSlotRequest(2L, START));

        assertThat(created.endTime()).isEqualTo(START.plusMinutes(60));
        verify(slots).insert(1, 2, START, START.plusMinutes(60));
    }

    @Test
    void overlappingSlotIsConflict() {
        when(services.findById(2)).thenReturn(Optional.of(CODING));
        when(slots.overlapsExisting(1, START, START.plusMinutes(60))).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.create(ALICE, new CreateSlotRequest(2L, START)));
        verify(slots, never()).insert(anyLong(), anyLong(), any(), any());
    }

    @Test
    void pastStartIsBadRequest() {
        when(services.findById(2)).thenReturn(Optional.of(CODING));

        assertThrows(IllegalArgumentException.class,
                () -> service.create(ALICE, new CreateSlotRequest(2L, NOW.minusHours(1))));
    }

    @Test
    void unknownServiceIsNotFound() {
        when(services.findById(9)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.create(ALICE, new CreateSlotRequest(9L, START)));
    }

    @Test
    void customersCannotCreateSlots() {
        assertThrows(ForbiddenException.class, () -> service.create(SAM, new CreateSlotRequest(2L, START)));
    }

    @Test
    void removesOwnOpenSlotWithVersionCheck() {
        when(slots.findById(30)).thenReturn(Optional.of(
                new Slot(30, 1, 2, START, START.plusHours(1), Slot.OPEN, 2)));
        when(slots.markRemoved(30, 2)).thenReturn(1);

        service.remove(ALICE, 30);

        verify(slots).markRemoved(30, 2);
    }

    @Test
    void cannotRemoveAnotherProvidersSlot() {
        when(slots.findById(30)).thenReturn(Optional.of(
                new Slot(30, 1, 2, START, START.plusHours(1), Slot.OPEN, 0)));

        assertThrows(ForbiddenException.class, () -> service.remove(RAJ, 30));
        verify(slots, never()).markRemoved(anyLong(), anyInt());
    }

    @Test
    void cannotRemoveBookedSlot() {
        when(slots.findById(30)).thenReturn(Optional.of(
                new Slot(30, 1, 2, START, START.plusHours(1), Slot.BOOKED, 1)));

        assertThrows(ConflictException.class, () -> service.remove(ALICE, 30));
    }

    @Test
    void removalLosesToAConcurrentBooking() {
        when(slots.findById(30)).thenReturn(Optional.of(
                new Slot(30, 1, 2, START, START.plusHours(1), Slot.OPEN, 0)));
        when(slots.markRemoved(30, 0)).thenReturn(0);

        assertThrows(ConflictException.class, () -> service.remove(ALICE, 30));
    }

    @Test
    void removedSlotIsNotFound() {
        when(slots.findById(30)).thenReturn(Optional.of(
                new Slot(30, 1, 2, START, START.plusHours(1), Slot.REMOVED, 1)));

        assertThrows(NotFoundException.class, () -> service.remove(ALICE, 30));
    }
}
