-- Sample data. Every seeded account's password is: password123
-- Each insert only runs when its table is empty, so restarts never duplicate or overwrite data.
-- Slot times are relative to CURRENT_DATE of the day the database was first seeded.

INSERT INTO users (username, password_hash, full_name, email, role)
SELECT * FROM (VALUES
    ('alice.mentor', '$2a$10$J5aZUzfFtU88IsVN.72nq.jJB73Mp2PQ0TiBAV27rX4JBRaJJcTXy', 'Alice Nguyen',   'alice@example.com', 'PROVIDER'),
    ('raj.mentor',   '$2a$10$J5aZUzfFtU88IsVN.72nq.jJB73Mp2PQ0TiBAV27rX4JBRaJJcTXy', 'Raj Patel',      'raj@example.com',   'PROVIDER'),
    ('maria.mentor', '$2a$10$J5aZUzfFtU88IsVN.72nq.jJB73Mp2PQ0TiBAV27rX4JBRaJJcTXy', 'Maria Gonzalez', 'maria@example.com', 'PROVIDER'),
    ('sam.dev',      '$2a$10$J5aZUzfFtU88IsVN.72nq.jJB73Mp2PQ0TiBAV27rX4JBRaJJcTXy', 'Sam Lee',        'sam@example.com',   'CUSTOMER'),
    ('jordan.dev',   '$2a$10$J5aZUzfFtU88IsVN.72nq.jJB73Mp2PQ0TiBAV27rX4JBRaJJcTXy', 'Jordan Kim',     'jordan@example.com','CUSTOMER')
) AS v (username, password_hash, full_name, email, role)
WHERE NOT EXISTS (SELECT 1 FROM users);

INSERT INTO providers (user_id, display_name, headline, bio)
SELECT * FROM (VALUES
    ((SELECT id FROM users WHERE username = 'alice.mentor'), 'Alice Nguyen',
        'Senior Backend Engineer, 8 yrs', 'Java/Spring, distributed systems, has run 200+ interview loops.'),
    ((SELECT id FROM users WHERE username = 'raj.mentor'), 'Raj Patel',
        'Staff Engineer, ex-FAANG', 'System design and large-scale infrastructure.'),
    ((SELECT id FROM users WHERE username = 'maria.mentor'), 'Maria Gonzalez',
        'Engineering Manager & Tech Recruiter', 'Resume reviews and behavioral interview coaching.')
) AS v (user_id, display_name, headline, bio)
WHERE NOT EXISTS (SELECT 1 FROM providers);

INSERT INTO services (name, description, duration_minutes, price_cents)
SELECT * FROM (VALUES
    ('Resume Review',             'Line-by-line feedback on your SWE resume.',             30, 2500),
    ('Mock Coding Interview',     'LeetCode-style problem with live feedback.',            60, 5000),
    ('Mock System Design',        'Design a large-scale system, then debrief.',            60, 6000),
    ('Behavioral Interview Prep', 'STAR-method practice for behavioral questions.',       45, 3500)
) AS v (name, description, duration_minutes, price_cents)
WHERE NOT EXISTS (SELECT 1 FROM services);

-- Slots: provider and service looked up by name; time = today + N days at HH:MM.
INSERT INTO availability_slots (provider_id, service_id, start_time, end_time)
SELECT * FROM (VALUES
    ((SELECT p.id FROM providers p JOIN users u ON u.id = p.user_id WHERE u.username = 'alice.mentor'),
     (SELECT id FROM services WHERE name = 'Mock Coding Interview'),
     CURRENT_DATE + 1 + TIME '10:00', CURRENT_DATE + 1 + TIME '11:00'),
    ((SELECT p.id FROM providers p JOIN users u ON u.id = p.user_id WHERE u.username = 'alice.mentor'),
     (SELECT id FROM services WHERE name = 'Mock Coding Interview'),
     CURRENT_DATE + 1 + TIME '13:00', CURRENT_DATE + 1 + TIME '14:00'),
    ((SELECT p.id FROM providers p JOIN users u ON u.id = p.user_id WHERE u.username = 'alice.mentor'),
     (SELECT id FROM services WHERE name = 'Resume Review'),
     CURRENT_DATE + 2 + TIME '09:00', CURRENT_DATE + 2 + TIME '09:30'),
    ((SELECT p.id FROM providers p JOIN users u ON u.id = p.user_id WHERE u.username = 'raj.mentor'),
     (SELECT id FROM services WHERE name = 'Mock System Design'),
     CURRENT_DATE + 1 + TIME '15:00', CURRENT_DATE + 1 + TIME '16:00'),
    ((SELECT p.id FROM providers p JOIN users u ON u.id = p.user_id WHERE u.username = 'raj.mentor'),
     (SELECT id FROM services WHERE name = 'Mock System Design'),
     CURRENT_DATE + 3 + TIME '15:00', CURRENT_DATE + 3 + TIME '16:00'),
    ((SELECT p.id FROM providers p JOIN users u ON u.id = p.user_id WHERE u.username = 'raj.mentor'),
     (SELECT id FROM services WHERE name = 'Mock Coding Interview'),
     CURRENT_DATE + 4 + TIME '18:00', CURRENT_DATE + 4 + TIME '19:00'),
    ((SELECT p.id FROM providers p JOIN users u ON u.id = p.user_id WHERE u.username = 'maria.mentor'),
     (SELECT id FROM services WHERE name = 'Resume Review'),
     CURRENT_DATE + 1 + TIME '09:00', CURRENT_DATE + 1 + TIME '09:30'),
    ((SELECT p.id FROM providers p JOIN users u ON u.id = p.user_id WHERE u.username = 'maria.mentor'),
     (SELECT id FROM services WHERE name = 'Resume Review'),
     CURRENT_DATE + 1 + TIME '09:30', CURRENT_DATE + 1 + TIME '10:00'),
    ((SELECT p.id FROM providers p JOIN users u ON u.id = p.user_id WHERE u.username = 'maria.mentor'),
     (SELECT id FROM services WHERE name = 'Behavioral Interview Prep'),
     CURRENT_DATE + 2 + TIME '11:00', CURRENT_DATE + 2 + TIME '11:45'),
    ((SELECT p.id FROM providers p JOIN users u ON u.id = p.user_id WHERE u.username = 'maria.mentor'),
     (SELECT id FROM services WHERE name = 'Behavioral Interview Prep'),
     CURRENT_DATE + 5 + TIME '14:00', CURRENT_DATE + 5 + TIME '14:45')
) AS v (provider_id, service_id, start_time, end_time)
WHERE NOT EXISTS (SELECT 1 FROM availability_slots);

-- One existing booking (Sam with Maria, tomorrow 09:00) so the slot list shows that BOOKED slots are hidden.
INSERT INTO appointments (slot_id, service_id, customer_id, notes)
SELECT s.id, s.service_id, (SELECT id FROM users WHERE username = 'sam.dev'), 'Targeting new-grad backend roles'
  FROM availability_slots s
 WHERE s.start_time = CURRENT_DATE + 1 + TIME '09:00'
   AND NOT EXISTS (SELECT 1 FROM appointments);

UPDATE availability_slots SET status = 'BOOKED', version = version + 1
 WHERE status = 'OPEN'
   AND id IN (SELECT slot_id FROM appointments WHERE status = 'BOOKED');
