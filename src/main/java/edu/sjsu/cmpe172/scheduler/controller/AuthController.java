package edu.sjsu.cmpe172.scheduler.controller;

import edu.sjsu.cmpe172.scheduler.dto.UserDto;
import edu.sjsu.cmpe172.scheduler.model.AppUser;
import edu.sjsu.cmpe172.scheduler.service.DtoMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login and logout themselves are handled by Spring Security (see SecurityConfig):
 * POST /api/auth/login and POST /api/auth/logout.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** The logged-in user, or 204 No Content if nobody is logged in. */
    @GetMapping("/me")
    public ResponseEntity<UserDto> me(@AuthenticationPrincipal(expression = "user") AppUser me) {
        return me == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(DtoMapper.toDto(me));
    }
}
