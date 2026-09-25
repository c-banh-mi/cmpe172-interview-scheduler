package edu.sjsu.cmpe172.scheduler.model;

/** Row of the services table (named ServiceOffering to avoid clashing with Spring's @Service). */
public record ServiceOffering(long id, String name, String description, int durationMinutes, int priceCents) {
}
