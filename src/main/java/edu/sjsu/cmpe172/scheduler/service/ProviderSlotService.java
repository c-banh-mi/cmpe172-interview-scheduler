package edu.sjsu.cmpe172.scheduler.service;

import edu.sjsu.cmpe172.scheduler.dto.CreateSlotRequest;
import edu.sjsu.cmpe172.scheduler.dto.ProviderSlotDto;
import edu.sjsu.cmpe172.scheduler.exception.ConflictException;
import edu.sjsu.cmpe172.scheduler.exception.ForbiddenException;
import edu.sjsu.cmpe172.scheduler.exception.NotFoundException;
import edu.sjsu.cmpe172.scheduler.model.AppUser;
import edu.sjsu.cmpe172.scheduler.model.ServiceOffering;
import edu.sjsu.cmpe172.scheduler.model.Slot;
import edu.sjsu.cmpe172.scheduler.repository.ServiceOfferingRepository;
import edu.sjsu.cmpe172.scheduler.repository.SlotRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** A provider managing their own availability. */
@Service
public class ProviderSlotService {

    private final SlotRepository slots;
    private final ServiceOfferingRepository services;
    private final Clock clock;

    public ProviderSlotService(SlotRepository slots, ServiceOfferingRepository services, Clock clock) {
        this.slots = slots;
        this.services = services;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ProviderSlotDto> listMine(AppUser provider) {
        return slots.findUpcomingByProvider(providerId(provider)).stream()
                .map(DtoMapper::toProviderSlotDto)
                .toList();
    }

    /** Creates an OPEN slot for one service; its length is the service's duration. */
    @Transactional
    public ProviderSlotDto create(AppUser provider, CreateSlotRequest req) {
        long providerId = providerId(provider);
        ServiceOffering service = services.findById(req.serviceId())
                .orElseThrow(() -> new NotFoundException("Service " + req.serviceId() + " not found"));

        LocalDateTime start = req.startTime().withSecond(0).withNano(0);
        if (!start.isAfter(LocalDateTime.now(clock))) {
            throw new IllegalArgumentException("startTime must be in the future");
        }
        LocalDateTime end = start.plusMinutes(service.durationMinutes());

        if (slots.overlapsExisting(providerId, start, end)) {
            throw new ConflictException("You already have a slot overlapping " + start + " to " + end);
        }
        long id;
        try {
            id = slots.insert(providerId, service.id(), start, end);
        } catch (DuplicateKeyException e) {
            // uq_slot_provider_start: a concurrent request created the same start time.
            throw new ConflictException("You already have a slot starting at " + start);
        }
        return slots.findViewById(id)
                .map(DtoMapper::toProviderSlotDto)
                .orElseThrow(() -> new NotFoundException("Slot " + id + " not found"));
    }

    /** Soft-deletes one of the provider's own OPEN slots. Booked slots must be cancelled by the customer first. */
    @Transactional
    public void remove(AppUser provider, long slotId) {
        long providerId = providerId(provider);
        Slot slot = slots.findById(slotId)
                .filter(s -> !Slot.REMOVED.equals(s.status()))
                .orElseThrow(() -> new NotFoundException("Slot " + slotId + " not found"));

        if (slot.providerId() != providerId) {
            throw new ForbiddenException("You can only remove your own slots");
        }
        if (!Slot.OPEN.equals(slot.status())) {
            throw new ConflictException("Slot " + slotId + " is booked and cannot be removed");
        }
        // Same version check as booking: if a customer booked it a moment ago, removal loses.
        if (slots.markRemoved(slotId, slot.version()) == 0) {
            throw new ConflictException("Slot " + slotId + " was just booked and cannot be removed");
        }
    }

    private static long providerId(AppUser user) {
        if (user.providerId() == null) {
            throw new ForbiddenException("Only providers can manage availability");
        }
        return user.providerId();
    }
}
