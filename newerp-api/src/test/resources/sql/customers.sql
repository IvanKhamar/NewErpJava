-- An invented customer with a group, an address and a cancelled guest order with the same phone (run after orders.sql).
DELETE FROM dd_customer; DELETE FROM dd_customer_group_description; DELETE FROM dd_address; DELETE FROM dd_customer_history;

INSERT INTO dd_customer_group_description (customer_group_id, language_id, name, description) VALUES (1, 3, 'Роздріб', '');
INSERT INTO dd_customer (customer_id, customer_group_id, language_id, firstname, lastname, email, telephone, fax, password, salt, address_id,
                         custom_field, ip, status, safe, token, code, date_added) VALUES
  (1, 1, 3, 'Олена', 'Коваль', 'olena@example.com', '0935554433', '', '', '', 7, '', '', 1, 0, '', '', '2024-05-01 00:00:00');
INSERT INTO dd_address (address_id, customer_id, firstname, lastname, company, address_1, address_2, city, postcode, custom_field) VALUES
  (7, 1, 'Олена', 'Коваль', '', 'Відділення №1', '', 'Київ', '', '');

INSERT INTO dd_order_status (order_status_id, language_id, name, color) VALUES (19, 3, 'Відмінено', '#f44336');
CREATE TEMPORARY TABLE guest_orders AS SELECT * FROM dd_order WHERE order_id = 1001;
UPDATE guest_orders SET order_id = 1050, customer_id = 0, telephone = '+38 (093) 555-44-33', order_status_id = 19, total = 700,
                        date_added = '2025-03-01 12:00:00';
INSERT INTO dd_order SELECT * FROM guest_orders;
DROP TEMPORARY TABLE guest_orders;
