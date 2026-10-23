-- Invented orders for ReportControllerTest (run after orders.sql, whose order 1001 is the template).
CREATE TEMPORARY TABLE report_orders AS SELECT * FROM dd_order WHERE order_id = 1001;
DELETE FROM dd_order; DELETE FROM dd_order_product; DELETE FROM dd_customer;
UPDATE report_orders SET order_id = 2001, order_status_id = 1, total = 1500, date_added = '2026-10-01 12:00:00';
INSERT INTO dd_order SELECT * FROM report_orders;
UPDATE report_orders SET order_id = 2002, order_status_id = 5, total = 2000, date_added = '2026-10-02 12:00:00';
INSERT INTO dd_order SELECT * FROM report_orders;
-- not finished (status 0): never counts
UPDATE report_orders SET order_id = 2003, order_status_id = 0, total = 999, date_added = '2026-10-03 12:00:00';
INSERT INTO dd_order SELECT * FROM report_orders;
UPDATE report_orders SET order_id = 2004, order_status_id = 1, total = 900, date_added = '2026-09-01 12:00:00';
INSERT INTO dd_order SELECT * FROM report_orders;
UPDATE report_orders SET order_id = 2005, order_status_id = 1, total = 1200, date_added = '2026-08-01 12:00:00';
INSERT INTO dd_order SELECT * FROM report_orders;
-- cancelled ("Відмінено"): counts only when chosen
UPDATE report_orders SET order_id = 2006, order_status_id = 19, total = 700, date_added = '2025-03-01 12:00:00';
INSERT INTO dd_order SELECT * FROM report_orders;
DROP TEMPORARY TABLE report_orders;
INSERT INTO dd_order_product (order_id, product_id, name, model, quantity, price, total, tax, reward) VALUES
  (2001, 50, 'Сукня лляна', 'SL-01', 2, 750, 1500, 0, 0), (2002, 51, 'Пояс', 'P-02', 1, 2000, 2000, 0, 0),
  (2003, 51, 'Пояс', 'P-02', 9, 111, 999, 0, 0);
