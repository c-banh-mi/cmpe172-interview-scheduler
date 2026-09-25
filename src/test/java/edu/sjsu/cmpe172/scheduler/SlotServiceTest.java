package edu.sjsu.cmpe172.scheduler;

import edu.sjsu.cmpe172.scheduler.dto.PageResponse;
import edu.sjsu.cmpe172.scheduler.dto.SlotDto;
import edu.sjsu.cmpe172.scheduler.dto.SlotFilter;
import edu.sjsu.cmpe172.scheduler.repository.SlotRepository;
import edu.sjsu.cmpe172.scheduler.service.SlotService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SlotServiceTest {

    private final SlotRepository repo = mock(SlotRepository.class);
    private final SlotService service = new SlotService(repo);
    private final SlotFilter noFilter = new SlotFilter(null, null, null);

    @Test
    void pageNumberBecomesSqlOffset() {
        when(repo.countOpen(noFilter)).thenReturn(25L);
        when(repo.findOpen(noFilter, 10, 20)).thenReturn(List.of());

        PageResponse<SlotDto> page = service.findOpenSlots(noFilter, 2, 10);

        verify(repo).findOpen(noFilter, 10, 20);
        assertThat(page.totalPages()).isEqualTo(3);
    }

    @Test
    void rejectsBadPaging() {
        assertThrows(IllegalArgumentException.class, () -> service.findOpenSlots(noFilter, -1, 10));
        assertThrows(IllegalArgumentException.class, () -> service.findOpenSlots(noFilter, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> service.findOpenSlots(noFilter, 0, SlotService.MAX_PAGE_SIZE + 1));
    }
}
