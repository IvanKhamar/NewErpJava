-- Invented shop products with sizes (run after users.sql). Product 60 is in category 30.
DELETE FROM dd_product; DELETE FROM dd_product_description; DELETE FROM dd_product_option_value; DELETE FROM dd_product_to_category;
DELETE FROM dd_option_description; DELETE FROM dd_option_value_description;

-- 60: a shirt with size M, 61: a scarf without options, 62: switched off (not offered)
INSERT INTO dd_product (product_id, model, sku, upc, ean, jan, isbn, mpn, location, quantity, stock_status_id, manufacturer_id, price, tax_class_id, date_available, status, date_added, date_modified) VALUES
  (60, 'T-2', '', '', '', '', '', '', '', 5, 0, 0, 900, 0, '2026-01-01', 1, '2026-01-01 00:00:00', '2026-01-01 00:00:00'),
  (61, 'SH-1', '', '', '', '', '', '', '', 3, 0, 0, 300, 0, '2026-01-01', 1, '2026-01-01 00:00:00', '2026-01-01 00:00:00'),
  (62, 'T-OLD', '', '', '', '', '', '', '', 0, 0, 0, 500, 0, '2026-01-01', 0, '2026-01-01 00:00:00', '2026-01-01 00:00:00');
INSERT INTO dd_product_description (product_id, language_id, name, description, tag, meta_title, meta_description, meta_keyword) VALUES
  (60, 3, 'Сорочка лляна оверсайз', '', '', '', '', ''), (61, 3, 'Шарф', '', '', '', '', ''),
  (62, 3, 'Сорочка лляна стара', '', '', '', '', '');
INSERT INTO dd_product_to_category (product_id, category_id) VALUES (60, 30);
INSERT INTO dd_option_description (option_id, language_id, name) VALUES (1, 3, 'Розмір');
INSERT INTO dd_option_value_description (option_value_id, language_id, option_id, name) VALUES (102, 3, 1, 'M');
INSERT INTO dd_product_option_value (product_option_value_id, product_option_id, product_id, option_id, option_value_id, quantity, subtract, price, price_prefix, points, points_prefix, weight, weight_prefix) VALUES
  (602, 6, 60, 1, 102, 5, 1, 0, '+', 0, '+', 0, '+');
