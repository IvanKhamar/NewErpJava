-- An active order for the sewing plan (run after users.sql, stock.sql, production.sql, orders.sql and catalog.sql).
DELETE FROM dd_category; DELETE FROM dd_order_option;

INSERT INTO dd_order_status (order_status_id, language_id, name, color) VALUES (17, 3, 'Уточнюємо', ''), (18, 3, 'Прийнято', '');
-- Category 7 makes its products in 10 days
INSERT INTO dd_category (category_id, image, parent_id, top, `column`, sort_order, status, date_added, date_modified, production_period) VALUES
  (7, NULL, 0, 0, 1, 0, 1, '2026-01-01 00:00:00', '2026-01-01 00:00:00', 10);
INSERT INTO dd_product_to_category (product_id, category_id) VALUES (60, 7);

-- Order 1006 ("Уточнюємо") wants 2 shirts of size M
INSERT INTO dd_order (order_id, invoice_no, invoice_prefix, store_id, store_name, store_url, customer_id, customer_group_id, firstname, lastname, email, telephone, fax, custom_field, payment_firstname, payment_lastname, payment_company, payment_address_1, payment_address_2, payment_city, payment_postcode, payment_country, payment_country_id, payment_zone, payment_zone_id, payment_address_format, payment_custom_field, payment_method, payment_code, shipping_firstname, shipping_lastname, shipping_company, shipping_address_1, shipping_address_2, shipping_city, shipping_postcode, shipping_country, shipping_country_id, shipping_zone, shipping_zone_id, shipping_address_format, shipping_custom_field, shipping_method, shipping_code, comment, total, order_status_id, affiliate_id, commission, marketing_id, tracking, language_id, currency_id, currency_code, currency_value, ip, forwarded_ip, user_agent, accept_language, date_added, date_modified, order_source_id) VALUES (1006, 0, '', 0, '', '', 0, 0, 'Марія', 'Лук', '', '+380631234567', '', '', '', '', '', '', '', '', '', '', 0, '', 0, '', '', '', '', '', '', '', '', '', '', '', '', 0, '', 0, '', '', '', '', '', '1800.0000', 17, 0, 0, 0, '', 3, 0, 'UAH', 1, '', '', '', '', '2026-10-01 10:00:00', '2026-10-01 10:00:00', 1);
INSERT INTO dd_order_product (order_id, product_id, name, model, quantity, price, total, tax, reward) VALUES
  (1006, 60, 'Сорочка лляна оверсайз', 'T-2', 2, 900, 1800, 0, 0);
INSERT INTO dd_order_option (order_id, order_product_id, product_option_id, product_option_value_id, name, value, type)
  SELECT order_id, order_product_id, 6, 602, 'Розмір', 'M', 'radio' FROM dd_order_product WHERE order_id = 1006;
