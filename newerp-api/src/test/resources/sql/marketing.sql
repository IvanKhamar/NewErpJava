-- Marketing data (run after orders.sql): order 1001 (Ірина, +380671112233) has a birthday today; questions and a subscriber.
DELETE FROM dd_coupon; DELETE FROM dd_sms_log; DELETE FROM dd_question; DELETE FROM dd_subscribe;
UPDATE dd_order SET birthday_day = DAY(NOW()), birthday_month = MONTH(NOW()), instagram = '@iryna' WHERE order_id = 1001;
INSERT INTO dd_question (name, telephone, question, date_added) VALUES
  ('Гість', '+38 (067) 111-22-33', 'Є розмір M?', '2026-10-05 10:00:00'),
  ('Гість', '093 555 11 22', '?', '2026-10-06 10:00:00'),
  ('Давно', '0931234567', '?', '2025-01-01 10:00:00');
INSERT INTO dd_subscribe (email, date_added) VALUES ('news@test.ua', '2026-10-05 10:00:00');
