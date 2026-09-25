-- Sample data. Every seeded account's password is: password123
-- Slot times are relative to CURRENT_DATE so they are always in the future after a restart.

INSERT INTO users (username, password_hash, full_name, email, role) VALUES
    ('alice.mentor', '$2a$10$J5aZUzfFtU88IsVN.72nq.jJB73Mp2PQ0TiBAV27rX4JBRaJJcTXy', 'Alice Nguyen',   'alice@example.com', 'PROVIDER'),
    ('raj.mentor',   '$2a$10$J5aZUzfFtU88IsVN.72nq.jJB73Mp2PQ0TiBAV27rX4JBRaJJcTXy', 'Raj Patel',      'raj@example.com',   'PROVIDER'),
    ('maria.mentor', '$2a$10$J5aZUzfFtU88IsVN.72nq.jJB73Mp2PQ0TiBAV27rX4JBRaJJcTXy', 'Maria Gonzalez', 'maria@example.com', 'PROVIDER'),
    ('sam.dev',      '$2a$10$J5aZUzfFtU88IsVN.72nq.jJB73Mp2PQ0TiBAV27rX4JBRaJJcTXy', 'Sam Lee',        'sam@example.com',   'CUSTOMER'),
    ('jordan.dev',   '$2a$10$J5aZUzfFtU88IsVN.72nq.jJB73Mp2PQ0TiBAV27rX4JBRaJJcTXy', 'Jordan Kim',     'jordan@example.com','CUSTOMER');

INSERT INTO providers (user_id, display_name, headline, bio) VALUES
    ((SELECT id FROM users WHERE username = 'alice.mentor'), 'Alice Nguyen',
        'Senior Backend Engineer, 8 yrs', 'Java/Spring, distributed systems, has run 200+ interview loops.'),
    ((SELECT id FROM users WHERE username = 'raj.mentor'), 'Raj Patel',
        'Staff Engineer, ex-FAANG', 'System design and large-scale infrastructure.'),
    ((SELECT id FROM users WHERE username = 'maria.mentor'), 'Maria Gonzalez',
        'Engineering Manager & Tech Recruiter', 'Resume reviews and behavioral interview coaching.');

INSERT INTO services (name, description, duration_minutes, price_cents) VALUES
    ('Resume Review',             'Line-by-line feedback on your SWE resume.',             30, 2500),
    ('Mock Coding Interview',     'LeetCode-style problem with live feedback.',            60, 5000),
    ('Mock System Design',        'Design a large-scale system, then debrief.',            60, 6000),
    ('Behavioral Interview Prep', 'STAR-method practice for behavioral questions.',       45, 3500);

-- Slots: provider and service looked up by name; time = today + N days at HH:MM.
INSERT INTO availability_slots (provider_id, service_id, start_time, end_time) VALUES
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
     CURRENT_DATE + 5 + TIME '14:00', CURRENT_DATE + 5 + TIME '14:45');

-- One existing booking so the slot list shows that BOOKED slots are hidden.
UPDATE availability_slots SET status = 'BOOKED', version = version + 1
 WHERE start_time = CURRENT_DATE + 1 + TIME '09:00';

INSERT INTO appointments (slot_id, service_id, customer_id, notes)
SELECT s.id, s.service_id, (SELECT id FROM users WHERE username = 'sam.dev'), 'Targeting new-grad backend roles'
  FROM availability_slots s
 WHERE s.start_time = CURRENT_DATE + 1 + TIME '09:00';
