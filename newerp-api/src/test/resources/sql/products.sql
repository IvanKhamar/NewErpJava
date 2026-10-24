-- Invented shop products for ProductControllerTest (run after users.sql). 70 and 72 are dresses, 71 is switched off.
DELETE FROM dd_product; DELETE FROM dd_product_description; DELETE FROM dd_product_option_value; DELETE FROM dd_product_to_category;
DELETE FROM dd_option_value_description; DELETE FROM dd_category; DELETE FROM dd_category_description;
DELETE FROM dd_product_label; DELETE FROM dd_product_to_label;
INSERT INTO dd_product (product_id, model, sku, upc, ean, jan, isbn, mpn, location, quantity, stock_status_id, manufacturer_id, price, tax_class_id, date_available, status, date_added, date_modified) VALUES
  (70, 'SL-01', '', '', '', '', '', '', '', 6, 0, 0, 1500, 0, '2026-01-01', 1, '2026-01-01 00:00:00', '2026-10-01 10:00:00'),
  (71, 'P-02', '', '', '', '', '', '', '', 0, 0, 0, 500, 0, '2026-01-01', 0, '2026-01-01 00:00:00', '2026-01-01 00:00:00'),
  (72, 'SL-50%', '', '', '', '', '', '', '', 3, 0, 0, 900, 0, '2026-01-01', 1, '2026-01-01 00:00:00', '2026-01-01 00:00:00');
INSERT INTO dd_product_description (product_id, language_id, name, description, tag, meta_title, meta_description, meta_keyword) VALUES
  (70, 3, 'Сукня лляна', '', '', '', '', ''), (71, 3, 'Пояс шкіряний', '', '', '', '', ''), (72, 3, 'Сукня шовкова', '', '', '', '', ''),
  (70, 1, 'Linen dress', '', '', '', '', '');
INSERT INTO dd_option_value_description (option_value_id, language_id, option_id, name) VALUES (102, 3, 1, 'M'), (103, 3, 1, 'L');
INSERT INTO dd_product_option_value (product_option_value_id, product_option_id, product_id, option_id, option_value_id, quantity, subtract, price, price_prefix, points, points_prefix, weight, weight_prefix) VALUES
  (701, 7, 70, 1, 102, 4, 1, 0, '+', 0, '+', 0, '+'), (702, 7, 70, 1, 103, 2, 1, 0, '+', 0, '+', 0, '+');
INSERT INTO dd_category (category_id, image, parent_id, top, `column`, sort_order, status, date_added, date_modified, production_period) VALUES
  (30, NULL, 0, 0, 1, 0, 1, '2026-01-01 00:00:00', '2026-01-01 00:00:00', 0),
  (31, NULL, 0, 0, 1, 0, 1, '2026-01-01 00:00:00', '2026-01-01 00:00:00', 0),
  (32, NULL, 0, 0, 1, 0, 0, '2026-01-01 00:00:00', '2026-01-01 00:00:00', 0);
INSERT INTO dd_category_description (category_id, language_id, name, description, meta_title, meta_description, meta_keyword) VALUES
  (30, 3, 'Сукні', '', '', '', ''), (31, 3, 'Аксесуари', '', '', '', ''), (32, 3, 'Архів', '', '', '', '');
INSERT INTO dd_product_to_category (product_id, category_id) VALUES (70, 30), (72, 30), (71, 31);
INSERT INTO dd_product_label (label_id, name, background_color, text_color, sort_order, status) VALUES
  (1, 'Хіт', '', '', 1, 1), (2, 'Новинка', '', '', 2, 1);
INSERT INTO dd_product_to_label (product_id, label_id) VALUES (70, 2), (70, 1);
