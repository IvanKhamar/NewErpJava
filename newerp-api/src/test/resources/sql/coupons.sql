-- Coupons (run after orders.sql): AUTUMN10 was used by placed order 1001 and by the unfinished cart 1004 (not counted);
-- OLD5 is an old site row with 0000-00-00 dates.
DELETE FROM dd_coupon; DELETE FROM dd_order_total;
SET SESSION sql_mode = '';
INSERT INTO dd_coupon (coupon_id, name, code, type, discount, logged, shipping, total, date_start, date_end, uses_total, uses_customer,
                       status, date_added) VALUES
  (1, 'Осінь −10%', 'AUTUMN10', 'P', 10, 0, 0, 500, '2026-09-01', '2026-11-30', 100, '1', 1, '2026-09-01 10:00:00'),
  (2, 'Старий', 'OLD5', 'F', 5, 0, 0, 0, '0000-00-00', '0000-00-00', 0, '', 0, '0000-00-00 00:00:00');
INSERT INTO dd_order_total (order_id, code, title, value, sort_order) VALUES
  (1001, 'sub_total', 'Сума', 1500, 1), (1001, 'coupon', 'Купон (AUTUMN10)', -150, 2), (1001, 'total', 'Всього', 1350, 9),
  (1004, 'coupon', 'Купон (AUTUMN10)', -10, 2);
