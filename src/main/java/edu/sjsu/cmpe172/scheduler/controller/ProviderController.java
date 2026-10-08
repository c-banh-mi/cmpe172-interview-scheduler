package edu.sjsu.cmpe172.scheduler.controller;

import edu.sjsu.cmpe172.scheduler.dto.AppointmentDto;
import edu.sjsu.cmpe172.scheduler.dto.CreateSlotRequest;
import edu.sjsu.cmpe172.scheduler.dto.ProviderSlotDto;
import edu.sjsu.cmpe172.scheduler.model.AppUser;
import edu.sjsu.cmpe172.scheduler.service.AppointmentService;
import edu.sjsu.cmpe172.scheduler.service.ProviderSlotService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/** Provider-only endpoints (SecurityConfig requires ROLE_PROVIDER for /api/provider/**). */
@RestController
@RequestMapping("/api/provider")
public class ProviderController {

    private final ProviderSlotService slotService;
    private final AppointmentService appointmentService;

    public ProviderController(ProviderSlotService slotService, AppointmentService appointmentService) {
        this.slotService = slotService;
        this.appointmentService = appointmentService;
    }

    @GetMapping("/slots")
    public List<ProviderSlotDto> mySlots(@AuthenticationPrincipal(expression = "user") AppUser me) {
        return slotService.listMine(me);
    }

    @PostMapping("/slots")
    public ResponseEntity<ProviderSlotDto> createSlot(@AuthenticationPrincipal(expression = "user") AppUser me,
                                                      @Valid @RequestBody CreateSlotRequest req) {
        ProviderSlotDto slot = slotService.create(me, req);
        return ResponseEntity.created(URI.create("/api/provider/slots/" + slot.id())).body(slot);
    }

    @DeleteMapping("/slots/{id}")
    public ResponseEntity<Void> removeSlot(@AuthenticationPrincipal(expression = "user") AppUser me,
                                           @PathVariable long id) {
        slotService.remove(me, id);
        return ResponseEntity.noContent().build();
    }

    /** Appointments customers booked with me. scope = upcoming | history. */
    @GetMapping("/appointments")
    public List<AppointmentDto> bookedWithMe(@AuthenticationPrincipal(expression = "user") AppUser me,
                                             @RequestParam(defaultValue = "upcoming") String scope) {
        return appointmentService.listForProvider(me, scope);
    }
}
