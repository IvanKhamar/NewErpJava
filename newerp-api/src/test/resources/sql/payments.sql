-- Invented PrivatBank payments for PaymentControllerTest (run after users.sql and orders.sql).
DELETE FROM dd_privatbank;
UPDATE dd_order SET prepayment = 300 WHERE order_id = 1001;

INSERT INTO dd_privatbank (payment_id, order_id, appcode, card, amount, cardamount, rest, description, terminal, trandate, trantime, status, show_status) VALUES
  (1, 0, 'APP-0001', 'UA000000000000000000000000001', '800.00', '800.00 UAH', '5000.00', 'Оплата за сукню, 2 шт', 'Петро Мельник', '2026-10-06', '11:30:00', 'r', 1),
  (2, 1001, 'APP-0002', 'UA000000000000000000000000001', '300.00', '300.00 UAH', '4200.00', 'Передоплата', 'Ірина Шевчук', '2026-10-01', '12:00:00', 'r', 1);
