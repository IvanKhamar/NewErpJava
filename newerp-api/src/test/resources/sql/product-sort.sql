-- Invented dresses, a label and orders for ProductSortControllerTest (run after users.sql and orders.sql).
DELETE FROM dd_product; DELETE FROM dd_product_description; DELETE FROM dd_product_to_category; DELETE FROM dd_category_description;
DELETE FROM dd_product_label; DELETE FROM dd_product_to_label; DELETE FROM dd_product_manual_pin; DELETE FROM dd_setting;
DELETE FROM dd_product_order_snapshot; DELETE FROM dd_product_order_snapshot_item; DELETE FROM dd_crosssell_sort; DELETE FROM dd_crosssell_item;
DELETE FROM dd_filter; DELETE FROM dd_filter_description; DELETE FROM dd_product_filter;

INSERT INTO dd_product (product_id, model, sku, upc, ean, jan, isbn, mpn, location, quantity, stock_status_id, manufacturer_id, price, tax_class_id, date_available, status, date_added, date_modified) VALUES
  (201, 'D-201', '', '', '', '', '', '', '', 5, 0, 0, 1000, 0, '2026-01-01', 1, '2026-01-01 00:00:00', '2026-01-01 00:00:00'),
  (202, 'D-202', '', '', '', '', '', '', '', 5, 0, 0, 1000, 0, '2026-01-01', 1, '2026-01-02 00:00:00', '2026-01-01 00:00:00'),
  (203, 'D-203', '', '', '', '', '', '', '', 5, 0, 0, 1000, 0, '2026-01-01', 1, '2026-01-03 00:00:00', '2026-01-01 00:00:00');
INSERT INTO dd_product_description (product_id, language_id, name, description, tag, meta_title, meta_description, meta_keyword) VALUES
  (201, 3, 'Сукня 201', '', '', '', '', ''), (202, 3, 'Сукня 202', '', '', '', '', ''), (203, 3, 'Сукня 203', '', '', '', '', '');
INSERT INTO dd_product_to_category (product_id, category_id) VALUES (201, 30), (202, 30), (203, 30);
INSERT INTO dd_category_description (category_id, language_id, name, description, meta_title, meta_description, meta_keyword) VALUES
  (30, 3, 'Сукні', '', '', '', '');
INSERT INTO dd_product_label (label_id, name, background_color, text_color, status) VALUES (1, 'Хіт', '', '', 1);
INSERT INTO dd_product_to_label (product_id, label_id) VALUES (201, 1), (202, 1);

-- 100 is a retail order; 101 is wholesale ("б2б" in the comment) and 102 was created by user 13: neither counts.
CREATE TEMPORARY TABLE sort_orders AS SELECT * FROM dd_order WHERE order_id = 1001;
UPDATE sort_orders SET order_id = 100, order_status_id = 17, date_added = '2026-10-05 12:00:00', comment = '', user_id = 0;
INSERT INTO dd_order SELECT * FROM sort_orders;
UPDATE sort_orders SET order_id = 101, comment = 'Замовлення б2б';
INSERT INTO dd_order SELECT * FROM sort_orders;
UPDATE sort_orders SET order_id = 102, comment = '', user_id = 13;
INSERT INTO dd_order SELECT * FROM sort_orders;
DROP TEMPORARY TABLE sort_orders;
INSERT INTO dd_order_product (order_id, product_id, name, model, quantity, price, total, tax, reward) VALUES
  (100, 201, 'Сукня', 'D', 1, 1000, 1000, 0, 0), (100, 202, 'Сукня', 'D', 3, 1000, 3000, 0, 0),
  (101, 203, 'Сукня', 'D', 50, 1000, 50000, 0, 0), (102, 203, 'Сукня', 'D', 50, 1000, 50000, 0, 0);
