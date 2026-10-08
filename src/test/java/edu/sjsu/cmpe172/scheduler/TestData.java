package edu.sjsu.cmpe172.scheduler;

import edu.sjsu.cmpe172.scheduler.model.AppUser;
import edu.sjsu.cmpe172.scheduler.model.Role;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Fixed users and a fixed clock for unit tests. */
final class TestData {

    static final ZoneId ZONE = ZoneId.of("America/Los_Angeles");
    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 7, 12, 0);
    static final Clock CLOCK = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);

    static final AppUser SAM = new AppUser(4, "sam.dev", "hash", "Sam Lee", Role.CUSTOMER, null);
    static final AppUser JORDAN = new AppUser(5, "jordan.dev", "hash", "Jordan Kim", Role.CUSTOMER, null);
    static final AppUser ALICE = new AppUser(1, "alice.mentor", "hash", "Alice Nguyen", Role.PROVIDER, 1L);
    static final AppUser RAJ = new AppUser(2, "raj.mentor", "hash", "Raj Patel", Role.PROVIDER, 2L);

    private TestData() {
    }
}
