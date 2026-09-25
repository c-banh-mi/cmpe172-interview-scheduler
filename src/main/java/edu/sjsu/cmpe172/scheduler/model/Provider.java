package edu.sjsu.cmpe172.scheduler.model;

/** Row of the providers table. */
public record Provider(long id, long userId, String displayName, String headline, String bio) {
}
