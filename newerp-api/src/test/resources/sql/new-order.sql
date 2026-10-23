-- Invented products and customers for NewOrderControllerTest (run after users.sql, stock.sql, production.sql and orders.sql).
DELETE FROM dd_product; DELETE FROM dd_product_description; DELETE FROM dd_product_option_value;
DELETE FROM dd_option_description; DELETE FROM dd_option_value_description; DELETE FROM dd_order_option; DELETE FROM dd_order_total;

INSERT INTO dd_order_status (order_status_id, language_id, name, color) VALUES (19, 3, 'Відмінено', ''), (20, 3, 'Відправлено', '');

-- 60: a shirt with size M, 61: a scarf without options, 62: switched off (not offered)
INSERT INTO dd_product (product_id, model, sku, upc, ean, jan, isbn, mpn, location, quantity, stock_status_id, manufacturer_id, price, tax_class_id, date_available, status, date_added, date_modified) VALUES
  (60, 'T-2', '', '', '', '', '', '', '', 5, 0, 0, 900, 0, '2026-01-01', 1, '2026-01-01 00:00:00', '2026-01-01 00:00:00'),
  (61, 'SH-1', '', '', '', '', '', '', '', 3, 0, 0, 300, 0, '2026-01-01', 1, '2026-01-01 00:00:00', '2026-01-01 00:00:00'),
  (62, 'T-OLD', '', '', '', '', '', '', '', 0, 0, 0, 500, 0, '2026-01-01', 0, '2026-01-01 00:00:00', '2026-01-01 00:00:00');
INSERT INTO dd_product_description (product_id, language_id, name, description, tag, meta_title, meta_description, meta_keyword) VALUES
  (60, 3, 'Сорочка лляна оверсайз', '', '', '', '', ''), (61, 3, 'Шарф', '', '', '', '', ''),
  (62, 3, 'Сорочка лляна стара', '', '', '', '', '');
INSERT INTO dd_option_description (option_id, language_id, name) VALUES (1, 3, 'Розмір');
INSERT INTO dd_option_value_description (option_value_id, language_id, option_id, name) VALUES (102, 3, 1, 'M');
INSERT INTO dd_product_option_value (product_option_value_id, product_option_id, product_id, option_id, option_value_id, quantity, subtract, price, price_prefix, points, points_prefix, weight, weight_prefix) VALUES
  (602, 6, 60, 1, 102, 5, 0, 0, '+', 0, '+', 0, '+');

-- The shirt is model 1 ("Сукня Лілія" in production.sql), size M, with 5 sewn
INSERT INTO dd_stock_production_products (production_id, product_id) VALUES (1, 60);
UPDATE dd_stock_production_sizes SET oc_option_value_id = 102, sewn_stock = 5 WHERE size_id = 2;

-- A customer who cancelled their only order; the phone is written with spaces and brackets
INSERT INTO dd_order (order_id, invoice_no, invoice_prefix, store_id, store_name, store_url, customer_id, customer_group_id, firstname, lastname, email, telephone, fax, custom_field, payment_firstname, payment_lastname, payment_company, payment_address_1, payment_address_2, payment_city, payment_postcode, payment_country, payment_country_id, payment_zone, payment_zone_id, payment_address_format, payment_custom_field, payment_method, payment_code, shipping_firstname, shipping_lastname, shipping_company, shipping_address_1, shipping_address_2, shipping_city, shipping_postcode, shipping_country, shipping_country_id, shipping_zone, shipping_zone_id, shipping_address_format, shipping_custom_field, shipping_method, shipping_code, comment, total, order_status_id, affiliate_id, commission, marketing_id, tracking, language_id, currency_id, currency_code, currency_value, ip, forwarded_ip, user_agent, accept_language, date_added, date_modified, order_source_id) VALUES (1005, 0, '', 0, '', '', 0, 0, 'Оксана', 'Відмова', '', '+38 (093) 555-44-33', '', '', '', '', '', '', '', '', '', '', 0, '', 0, '', '', 'Накладений платіж', 'cod', '', '', '', '', '', '', '', '', 0, '', 0, '', '', 'Нова Пошта', 'novaposhta.warehouse', '', '700.0000', 19, 0, 0, 0, '', 3, 0, 'UAH', 1, '', '', '', '', '2026-09-01 10:00:00', '2026-09-01 10:00:00', 1);
