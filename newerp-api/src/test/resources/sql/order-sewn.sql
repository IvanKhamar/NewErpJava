-- Order 1001 with sizes, for the sewn stock write-off (run after production.sql and orders.sql).
DELETE FROM dd_order_option; DELETE FROM dd_product_option_value; DELETE FROM dd_product_to_category;
DELETE FROM dd_product_description; DELETE FROM dd_sms_log;

INSERT INTO dd_order_status (order_status_id, language_id, name, color) VALUES (20, 3, 'Відправлено', ''), (22, 3, 'Отримано', '');
-- 52 is linked directly to model 2, but the order has no size option for it
INSERT INTO dd_order_product (order_id, product_id, name, model, quantity, price, total, tax, reward) VALUES
  (1001, 52, 'Футболка', 'F-03', 1, 300, 300, 0, 0);
INSERT INTO dd_stock_production_products (production_id, product_id) VALUES (2, 52);
-- 51 "Пояс Лілія" is linked to model 1 through category 20 with the filter "Лілія"
INSERT INTO dd_product_to_category (product_id, category_id) VALUES (51, 20);
INSERT INTO dd_product_description (product_id, language_id, name, description, tag, meta_title, meta_description, meta_keyword) VALUES
  (51, 3, 'Пояс Лілія', '', '', '', '', '');
-- Both lines of model 1 are size S (option value 101)
INSERT INTO dd_product_option_value (product_option_value_id, product_option_id, product_id, option_id, option_value_id, quantity, subtract, price, price_prefix, points, points_prefix, weight, weight_prefix) VALUES
  (501, 1, 50, 1, 101, 0, 0, 0, '+', 0, '+', 0, '+'),
  (502, 2, 51, 1, 101, 0, 0, 0, '+', 0, '+', 0, '+');
INSERT INTO dd_order_option (order_id, order_product_id, product_option_id, product_option_value_id, name, value, type)
  SELECT order_id, order_product_id, 1, 501, 'Розмір', 'S', 'select' FROM dd_order_product WHERE order_id = 1001 AND product_id = 50;
INSERT INTO dd_order_option (order_id, order_product_id, product_option_id, product_option_value_id, name, value, type)
  SELECT order_id, order_product_id, 2, 502, 'Розмір', 'S', 'select' FROM dd_order_product WHERE order_id = 1001 AND product_id = 51;
