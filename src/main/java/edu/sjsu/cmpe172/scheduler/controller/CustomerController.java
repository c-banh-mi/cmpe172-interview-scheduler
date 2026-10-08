package edu.sjsu.cmpe172.scheduler.controller;

import edu.sjsu.cmpe172.scheduler.dto.AppointmentDto;
import edu.sjsu.cmpe172.scheduler.dto.BookingRequest;
import edu.sjsu.cmpe172.scheduler.model.AppUser;
import edu.sjsu.cmpe172.scheduler.service.AppointmentService;
import edu.sjsu.cmpe172.scheduler.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/** Customer-only endpoints (SecurityConfig requires ROLE_CUSTOMER for /api/customer/**). */
@RestController
@RequestMapping("/api/customer/appointments")
public class CustomerController {

    private final BookingService bookingService;
    private final AppointmentService appointmentService;

    public CustomerController(BookingService bookingService, AppointmentService appointmentService) {
        this.bookingService = bookingService;
        this.appointmentService = appointmentService;
    }

    /** Book a slot. 201 with the appointment, 409 if the slot was taken. */
    @PostMapping
    public ResponseEntity<AppointmentDto> book(@AuthenticationPrincipal(expression = "user") AppUser me,
                                               @Valid @RequestBody BookingRequest req) {
        AppointmentDto booked = bookingService.book(me, req);
        return ResponseEntity.created(URI.create("/api/customer/appointments/" + booked.id())).body(booked);
    }

    /** e.g. GET /api/customer/appointments?scope=history */
    @GetMapping
    public List<AppointmentDto> mine(@AuthenticationPrincipal(expression = "user") AppUser me,
                                     @RequestParam(defaultValue = "upcoming") String scope) {
        return appointmentService.listForCustomer(me, scope);
    }

    @PostMapping("/{id}/cancel")
    public AppointmentDto cancel(@AuthenticationPrincipal(expression = "user") AppUser me, @PathVariable long id) {
        return appointmentService.cancel(me, id);
    }
}
