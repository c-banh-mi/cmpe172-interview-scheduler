package edu.sjsu.cmpe172.scheduler.model;

import java.time.LocalDate;

/** Optional filters for browsing open slots. Null means "don't filter on this". */
public record SlotFilter(Long providerId, Long serviceId, LocalDate date) {
}
