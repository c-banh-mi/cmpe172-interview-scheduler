package edu.sjsu.cmpe172.scheduler.dto;

import edu.sjsu.cmpe172.scheduler.model.Role;

/** The logged-in user as the frontend sees it (no password hash). */
public record UserDto(long id, String username, String fullName, Role role) {
}
