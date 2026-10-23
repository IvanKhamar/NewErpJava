-- Invented Checkbox cashiers (run after orders.sql). Cashier 1 is the default one (no payment code),
-- cashier 2 takes cash-on-delivery orders and closes the shift at 21:00 by itself.
DELETE FROM dd_cashbox_cheque; DELETE FROM dd_cashbox_report; DELETE FROM dd_cashbox_work_shift;
DELETE FROM dd_cashbox_user; DELETE FROM dd_cashbox_warehouse; DELETE FROM dd_cashbox_requisites;

INSERT INTO dd_cashbox_user (user_id, name, description, payment_code, auto_close_time, warehouse_id, requisites_id, status, date_added) VALUES
  (1, 'Олена', '', '', NULL, 0, 0, 1, '2026-01-01'),
  (2, 'Марта (НП)', '', 'cod', '21:00:00', 0, 0, 1, '2026-01-01');

-- Order 1001: cash on delivery, 1500 with a prepayment of 300 already received.
UPDATE dd_order SET payment_code = 'cod', prepayment = 300 WHERE order_id = 1001;
