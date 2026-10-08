# Milestone 2 report material (facts and code only)

Generated from commit `d244d3d`. Line numbers match that commit.

## Schema changes since `milestone-1` (`git diff milestone-1 -- src/main/resources/schema.sql src/main/resources/seed.sql`)

schema.sql (36 lines changed):
- Removed the 5 `DROP TABLE IF EXISTS` statements: data is no longer wiped on startup.
- All `CREATE TABLE` / `CREATE INDEX` statements are now `... IF NOT EXISTS` (safe to run on every startup).
- `availability_slots.uq_slot_provider_start` changed from a table constraint `UNIQUE (provider_id, start_time)`
  to a partial unique index `ON availability_slots (provider_id, start_time) WHERE status <> 'REMOVED'`
  (a removed slot no longer blocks re-adding the same start time).
- Added `ALTER TABLE availability_slots DROP CONSTRAINT IF EXISTS uq_slot_provider_start;` to upgrade an
  existing Milestone 1 database (no-op on a new one).
- `availability_slots.version` unchanged (`INT NOT NULL DEFAULT 0`); its comment now says it is incremented on
  every status change.
- Unchanged: all columns, CHECK constraints, foreign keys, `uq_slot_id_service`, the double-booking guard
  `uq_appt_active_slot ON appointments (slot_id) WHERE status = 'BOOKED'`, `ix_slots_open_start`, `ix_appt_customer`.

seed.sql (43 lines changed):
- Each `INSERT ... VALUES` became `INSERT ... SELECT * FROM (VALUES ...) AS v (...) WHERE NOT EXISTS (SELECT 1 FROM <table>)`,
  so seed rows are inserted only into empty tables.
- The seeded booking (sam.dev, Maria's Resume Review, tomorrow 09:00) is inserted first, guarded by
  `NOT EXISTS (SELECT 1 FROM appointments)`, then its slot is set to BOOKED with `version = version + 1`.
- Same seed data as Milestone 1: 5 users (BCrypt `password123`), 3 providers, 4 services, 10 slots, 1 appointment.

## Code snippets

### Version-check UPDATE (optimistic lock)

`src/main/java/edu/sjsu/cmpe172/scheduler/repository/SlotRepository.java:102-119`

```java
 102      /**
 103       * OPTIMISTIC LOCK. Flips OPEN -> BOOKED only if nobody changed the row since we read it
 104       * (same version). Returns rows updated: 1 = we won, 0 = someone else got there first.
 105       *
 106       * Under READ COMMITTED, PostgreSQL makes a concurrent second UPDATE wait for the first
 107       * transaction's row lock, then re-checks this WHERE clause against the committed row,
 108       * where the version has already moved on, so it matches 0 rows.
 109       */
 110      public int markBooked(long id, int expectedVersion) {
 111          return jdbc.sql("""
 112                          UPDATE availability_slots
 113                             SET status = 'BOOKED', version = version + 1
 114                           WHERE id = :id AND version = :version AND status = 'OPEN'
 115                          """)
 116                  .param("id", id)
 117                  .param("version", expectedVersion)
 118                  .update();
 119      }
```

### SlotBooker: the @Transactional booking attempt

`src/main/java/edu/sjsu/cmpe172/scheduler/service/SlotBooker.java:36-69`

```java
  36      /**
  37       * Read the slot, check the booking rules, then claim it with a version-checked UPDATE
  38       * and insert the appointment. Both writes commit together or not at all.
  39       *
  40       * @return the new appointment's id
  41       * @throws OptimisticLockingFailureException if another transaction changed the slot
  42       *         between our read and our update (the caller retries)
  43       */
  44      @Transactional(isolation = Isolation.READ_COMMITTED)
  45      public long bookOnce(long customerId, BookingRequest req) {
  46          Slot slot = slots.findById(req.slotId())
  47                  .orElseThrow(() -> new NotFoundException("Slot " + req.slotId() + " not found"));
  48  
  49          if (slot.serviceId() != req.serviceId()) {
  50              throw new IllegalArgumentException("Slot " + slot.id() + " is not offered for service " + req.serviceId());
  51          }
  52          if (!Slot.OPEN.equals(slot.status())) {
  53              throw new SlotUnavailableException("Slot " + slot.id() + " is no longer available");
  54          }
  55          if (!slot.startTime().isAfter(LocalDateTime.now(clock))) {
  56              throw new SlotUnavailableException("Slot " + slot.id() + " has already started");
  57          }
  58          if (appointments.customerHasOverlap(customerId, slot.startTime(), slot.endTime())) {
  59              throw new ConflictException("You already have an appointment at that time");
  60          }
  61  
  62          // The concurrency check: succeeds only if the row still has the version we read above.
  63          if (slots.markBooked(slot.id(), slot.version()) == 0) {
  64              throw new OptimisticLockingFailureException("Slot " + slot.id() + " changed while booking");
  65          }
  66          // Backstop: the partial unique index uq_appt_active_slot rejects a second BOOKED row
  67          // for this slot even if the check above were ever bypassed (DuplicateKeyException).
  68          return appointments.insert(slot.id(), slot.serviceId(), customerId, req.notes());
  69      }
```

### BookingService: retry loop and back-off

`src/main/java/edu/sjsu/cmpe172/scheduler/service/BookingService.java:39-77`

```java
  39      public AppointmentDto book(AppUser customer, BookingRequest req) {
  40          // The URL rule in SecurityConfig already blocks providers; this keeps the rule true
  41          // even if the service is called from somewhere else.
  42          if (customer.role() != Role.CUSTOMER) {
  43              throw new ForbiddenException("Only customers can book appointments");
  44          }
  45  
  46          for (int attempt = 1; ; attempt++) {
  47              try {
  48                  long id = booker.bookOnce(customer.id(), req);
  49                  log.info("Customer {} booked slot {} (appointment {}, attempt {})",
  50                          customer.username(), req.slotId(), id, attempt);
  51                  return appointments.findViewById(id)
  52                          .map(DtoMapper::toDto)
  53                          .orElseThrow(() -> new NotFoundException("Appointment " + id + " not found"));
  54              } catch (OptimisticLockingFailureException e) {
  55                  // Lost a race. Retrying re-reads the slot: if the winner booked it, the next
  56                  // attempt fails fast with 409; if the change was harmless, we can still book.
  57                  log.info("Version conflict booking slot {} (attempt {}/{})", req.slotId(), attempt, MAX_ATTEMPTS);
  58                  if (attempt == MAX_ATTEMPTS) {
  59                      throw new SlotUnavailableException("Slot " + req.slotId() + " is busy, please try again");
  60                  }
  61                  backOff(attempt);
  62              } catch (DuplicateKeyException e) {
  63                  // The unique index caught a second active booking for this slot.
  64                  throw new SlotUnavailableException("Slot " + req.slotId() + " is no longer available");
  65              }
  66          }
  67      }
  68  
  69      /** Short randomized pause so two retrying requests do not collide again in lockstep. */
  70      private static void backOff(int attempt) {
  71          try {
  72              Thread.sleep(ThreadLocalRandom.current().nextLong(10, 50) * attempt);
  73          } catch (InterruptedException e) {
  74              Thread.currentThread().interrupt();
  75              throw new SlotUnavailableException("Booking interrupted");
  76          }
  77      }
```

### SecurityConfig: URL role rules, JSON 401/403 handlers, CSRF

`src/main/java/edu/sjsu/cmpe172/scheduler/security/SecurityConfig.java:53-92`

```java
  53      @Bean
  54      SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
  55          http
  56                  .authorizeHttpRequests(auth -> auth
  57                          .requestMatchers("/api/customer/**").hasRole("CUSTOMER")
  58                          .requestMatchers("/api/provider/**").hasRole("PROVIDER")
  59                          .requestMatchers(HttpMethod.GET, "/api/home", "/api/slots", "/api/slots/*", "/api/services", "/api/providers")
  60                                  .permitAll()
  61                          .requestMatchers("/api/auth/**").permitAll()
  62                          .requestMatchers("/actuator/health", "/actuator/info").permitAll()
  63                          .requestMatchers(HttpMethod.GET, "/", "/index.html", "/app.js", "/app.css", "/favicon.ico")
  64                                  .permitAll()
  65                          .requestMatchers("/error").permitAll()
  66                          .anyRequest().authenticated())
  67                  .formLogin(form -> form
  68                          .loginPage("/")
  69                          .loginProcessingUrl("/api/auth/login")
  70                          .successHandler((req, res, auth) -> {
  71                              loadCsrfToken(req);
  72                              UserPrincipal p = (UserPrincipal) auth.getPrincipal();
  73                              writeJson(res, HttpStatus.OK, DtoMapper.toDto(p.user()));
  74                          })
  75                          .failureHandler((req, res, ex) ->
  76                                  writeError(req, res, HttpStatus.UNAUTHORIZED, "Invalid username or password")))
  77                  .logout(logout -> logout
  78                          .logoutUrl("/api/auth/logout")
  79                          .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
  80                  .exceptionHandling(ex -> ex
  81                          .authenticationEntryPoint((req, res, e) ->
  82                                  writeError(req, res, HttpStatus.UNAUTHORIZED, "Please log in"))
  83                          .accessDeniedHandler((req, res, e) ->
  84                                  writeError(req, res, HttpStatus.FORBIDDEN, "Your role is not allowed to do this")))
  85                  // CSRF for a JavaScript frontend: the token is sent as a readable cookie XSRF-TOKEN,
  86                  // and the page echoes it back in the X-XSRF-TOKEN header on POST/DELETE.
  87                  .csrf(csrf -> csrf
  88                          .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
  89                          .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
  90                  .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);
  91          return http.build();
  92      }
```

### UserRepository: role read from the users row (JDBC)

`src/main/java/edu/sjsu/cmpe172/scheduler/repository/UserRepository.java:28-39`

```java
  28      /** The user's row (role included) plus their provider profile id, if any. Used at login. */
  29      public Optional<AppUser> findByUsername(String username) {
  30          return jdbc.sql("""
  31                          SELECT u.id, u.username, u.password_hash, u.full_name, u.role, p.id AS provider_id
  32                            FROM users u
  33                            LEFT JOIN providers p ON p.user_id = u.id
  34                           WHERE u.username = :username
  35                          """)
  36                  .param("username", username)
  37                  .query(MAPPER)
  38                  .optional();
  39      }
```

### AppointmentService: owner-only cancel

`src/main/java/edu/sjsu/cmpe172/scheduler/service/AppointmentService.java:51-78`

```java
  51      /**
  52       * Owner-only cancel. Marks the appointment CANCELLED and reopens the slot in the same
  53       * transaction, so the slot is never left BOOKED with no active appointment (or vice versa).
  54       */
  55      @Transactional(isolation = Isolation.READ_COMMITTED)
  56      public AppointmentDto cancel(AppUser customer, long appointmentId) {
  57          Appointment appt = appointments.findById(appointmentId)
  58                  .orElseThrow(() -> new NotFoundException("Appointment " + appointmentId + " not found"));
  59  
  60          if (appt.customerId() != customer.id()) {
  61              throw new ForbiddenException("You can only cancel your own appointments");
  62          }
  63          if (!Appointment.BOOKED.equals(appt.status())) {
  64              throw new ConflictException("Appointment " + appointmentId + " is already " + appt.status());
  65          }
  66          if (!appt.startTime().isAfter(LocalDateTime.now(clock))) {
  67              throw new ConflictException("Appointments that have already started cannot be cancelled");
  68          }
  69          // Conditional update: if two cancel requests race, only one changes the row.
  70          if (appointments.cancel(appointmentId) == 0) {
  71              throw new ConflictException("Appointment " + appointmentId + " was already cancelled");
  72          }
  73          slots.reopen(appt.slotId());
  74  
  75          return appointments.findViewById(appointmentId)
  76                  .map(DtoMapper::toDto)
  77                  .orElseThrow(() -> new NotFoundException("Appointment " + appointmentId + " not found"));
  78      }
```

### Database backstop (unique index on active appointments)

`src/main/resources/schema.sql:72-74`

```sql
  72  -- DOUBLE-BOOKING GUARD: at most one *active* (BOOKED) appointment per slot.
  73  -- Cancelled rows stay for history and don't block rebooking the slot.
  74  CREATE UNIQUE INDEX IF NOT EXISTS uq_appt_active_slot ON appointments (slot_id) WHERE status = 'BOOKED';
```

### ConcurrentBookingTest: two threads, one slot

`src/test/java/edu/sjsu/cmpe172/scheduler/ConcurrentBookingTest.java:44-86`

```java
  44      /** Two threads, one slot, released at the same instant: exactly one booking succeeds. */
  45      @RepeatedTest(10)
  46      void twoConcurrentBookingsOfSameSlotExactlyOneSucceeds() throws Exception {
  47          long slotId = openSlotId("alice.mentor", "Mock Coding Interview");
  48          BookingRequest req = new BookingRequest(slotId, serviceIdOfSlot(slotId), null);
  49          AppUser sam = account("sam.dev");
  50          AppUser jordan = account("jordan.dev");
  51          int versionBefore = slots.findById(slotId).orElseThrow().version();
  52  
  53          // Both threads wait at the barrier, then call book() at the same moment.
  54          CyclicBarrier startTogether = new CyclicBarrier(2);
  55          List<Callable<AppointmentDto>> attempts = List.of(
  56                  () -> { startTogether.await(); return bookingService.book(sam, req); },
  57                  () -> { startTogether.await(); return bookingService.book(jordan, req); });
  58  
  59          int succeeded = 0;
  60          List<Throwable> failures = new ArrayList<>();
  61          ExecutorService pool = Executors.newFixedThreadPool(2);
  62          try {
  63              for (Future<AppointmentDto> result : pool.invokeAll(attempts, 30, TimeUnit.SECONDS)) {
  64                  try {
  65                      result.get();
  66                      succeeded++;
  67                  } catch (ExecutionException e) {
  68                      failures.add(e.getCause());
  69                  }
  70              }
  71          } finally {
  72              pool.shutdownNow();
  73          }
  74  
  75          assertThat(succeeded).as("successful bookings").isEqualTo(1);
  76          assertThat(failures).hasSize(1);
  77          assertThat(failures.get(0)).isInstanceOf(SlotUnavailableException.class);   // -> HTTP 409
  78  
  79          // The database agrees: one active appointment, slot BOOKED, version bumped exactly once.
  80          long active = jdbc.sql("SELECT COUNT(*) FROM appointments WHERE slot_id = :id AND status = 'BOOKED'")
  81                  .param("id", slotId).query(Long.class).single();
  82          Slot after = slots.findById(slotId).orElseThrow();
  83          assertThat(active).isEqualTo(1);
  84          assertThat(after.status()).isEqualTo(Slot.BOOKED);
  85          assertThat(after.version()).isEqualTo(versionBefore + 1);
  86      }
```

Note: the test starts both threads together with a `CyclicBarrier(2)` (line 54), not a CountDownLatch.
