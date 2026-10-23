-- Invented return lookups (run after orders.sql; order 1001 has 2 belts, product 51).
DELETE FROM dd_return; DELETE FROM dd_return_history; DELETE FROM dd_return_status; DELETE FROM dd_return_reason; DELETE FROM dd_return_action;
INSERT INTO dd_return_status (return_status_id, language_id, name) VALUES (1, 3, 'Очікує'), (2, 3, 'Отримано');
INSERT INTO dd_return_reason (return_reason_id, language_id, name) VALUES (1, 3, 'Не підійшов розмір');
INSERT INTO dd_return_action (return_action_id, language_id, name) VALUES (1, 3, 'Повернено кошти');
