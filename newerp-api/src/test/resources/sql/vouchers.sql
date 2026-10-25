-- Invented gift vouchers for VoucherControllerTest (run after orders.sql: order 1001 is "Нове", 1002 "Виконано").
DELETE FROM dd_voucher; DELETE FROM dd_voucher_history; DELETE FROM dd_voucher_theme; DELETE FROM dd_voucher_theme_description;
DELETE FROM dd_sms_log;
INSERT INTO dd_voucher_theme (voucher_theme_id, image) VALUES (1, 'catalog/demo/canon_eos_5d_2.jpg');
INSERT INTO dd_voucher_theme_description (voucher_theme_id, language_id, name) VALUES (1, 3, 'Свято'), (1, 1, 'Holiday');
INSERT INTO dd_voucher (voucher_id, order_id, code, from_name, from_email, to_name, to_email, voucher_theme_id, message, amount, status, date_added) VALUES
  -- made in the admin, partly spent in order 1001
  (1, 0, 'GIFT1000', 'Магазин', '', 'Олена', '', 1, 'З днем народження!', 1000, 1, '2026-09-01 10:00:00'),
  -- bought in order 1001, which is not paid yet
  (2, 1001, 'SHOP500', 'Ірина', '', 'Марія', '', 0, '', 500, 1, '2026-10-01 10:00:00'),
  -- bought in order 1002, which is paid
  (3, 1002, 'SHOP700', 'Петро', '', 'Андрій', '', 0, '', 700, 1, '2026-10-05 12:00:00'),
  (4, 0, 'OFF200', 'Магазин', '', 'Тарас', '', 0, '', 200, 0, '2026-08-01 10:00:00');
INSERT INTO dd_voucher_history (voucher_history_id, voucher_id, order_id, amount, date_added) VALUES
  (1, 1, 1001, -350, '2026-10-01 10:00:00');
