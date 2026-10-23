-- Invented customers for NewOrderControllerTest (run after users.sql, stock.sql, production.sql, orders.sql and catalog.sql).
DELETE FROM dd_order_option; DELETE FROM dd_order_total;

INSERT INTO dd_order_status (order_status_id, language_id, name, color) VALUES (19, 3, 'Відмінено', ''), (20, 3, 'Відправлено', '');

-- The shirt is model 1 ("Сукня Лілія" in production.sql), size M, with 5 sewn
INSERT INTO dd_stock_production_products (production_id, product_id) VALUES (1, 60);
UPDATE dd_stock_production_sizes SET oc_option_value_id = 102, sewn_stock = 5 WHERE size_id = 2;

-- A customer who cancelled their only order; the phone is written with spaces and brackets
INSERT INTO dd_order (order_id, invoice_no, invoice_prefix, store_id, store_name, store_url, customer_id, customer_group_id, firstname, lastname, email, telephone, fax, custom_field, payment_firstname, payment_lastname, payment_company, payment_address_1, payment_address_2, payment_city, payment_postcode, payment_country, payment_country_id, payment_zone, payment_zone_id, payment_address_format, payment_custom_field, payment_method, payment_code, shipping_firstname, shipping_lastname, shipping_company, shipping_address_1, shipping_address_2, shipping_city, shipping_postcode, shipping_country, shipping_country_id, shipping_zone, shipping_zone_id, shipping_address_format, shipping_custom_field, shipping_method, shipping_code, comment, total, order_status_id, affiliate_id, commission, marketing_id, tracking, language_id, currency_id, currency_code, currency_value, ip, forwarded_ip, user_agent, accept_language, date_added, date_modified, order_source_id) VALUES (1005, 0, '', 0, '', '', 0, 0, 'Оксана', 'Відмова', '', '+38 (093) 555-44-33', '', '', '', '', '', '', '', '', '', '', 0, '', 0, '', '', 'Накладений платіж', 'cod', '', '', '', '', '', '', '', '', 0, '', 0, '', '', 'Нова Пошта', 'novaposhta.warehouse', '', '700.0000', 19, 0, 0, 0, '', 3, 0, 'UAH', 1, '', '', '', '', '2026-09-01 10:00:00', '2026-09-01 10:00:00', 1);
