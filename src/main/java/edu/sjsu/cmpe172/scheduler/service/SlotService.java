package edu.sjsu.cmpe172.scheduler.service;

import edu.sjsu.cmpe172.scheduler.dto.PageResponse;
import edu.sjsu.cmpe172.scheduler.dto.SlotDto;
import edu.sjsu.cmpe172.scheduler.model.SlotFilter;
import edu.sjsu.cmpe172.scheduler.repository.SlotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class SlotService {

    public static final int MAX_PAGE_SIZE = 50;

    private final SlotRepository slots;

    public SlotService(SlotRepository slots) {
        this.slots = slots;
    }

    /**
     * Browse open slots with optional filters. {@code page} is zero-based and
     * translates to SQL {@code LIMIT size OFFSET page * size}.
     */
    public PageResponse<SlotDto> findOpenSlots(SlotFilter filter, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        long total = slots.countOpen(filter);
        List<SlotDto> items = slots.findOpen(filter, size, page * size).stream()
                .map(DtoMapper::toDto)
                .toList();
        return PageResponse.of(items, page, size, total);
    }
}
