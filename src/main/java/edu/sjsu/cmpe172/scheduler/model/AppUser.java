package edu.sjsu.cmpe172.scheduler.model;

/**
 * Row of the users table, plus the provider profile id when the user is a PROVIDER
 * (null for customers). Loaded once at login and kept in the session.
 */
public record AppUser(long id, String username, String passwordHash, String fullName, Role role, Long providerId) {
}
