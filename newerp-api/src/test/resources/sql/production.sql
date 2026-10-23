-- Invented garment models for the production tests (run after users.sql and stock.sql).
DELETE FROM erp_production_location; DELETE FROM erp_production_transfer;
DELETE FROM dd_stock_production_log; DELETE FROM dd_stock_production_component_stock;
DELETE FROM dd_stock_production_component_bom; DELETE FROM dd_stock_production_sewn_bom;
DELETE FROM dd_stock_production_components; DELETE FROM dd_stock_production_products;
DELETE FROM dd_stock_production_categories; DELETE FROM dd_stock_production_sizes; DELETE FROM dd_stock_production;

INSERT INTO dd_stock_production (production_id, name, type, min_total, sort_order, status) VALUES
  (1, 'Сукня Лілія', 'clothing', 10, 1, 1),
  (2, 'Футболка базова', 'ready_made', 5, 2, 1),
  (3, 'Стара модель', 'clothing', 50, 3, 0);
INSERT INTO dd_stock_production_sizes (size_id, production_id, size_name, percent, cut_stock, sewn_stock, sort_order, oc_option_value_id) VALUES
  (1, 1, 'S', 40, 2, 3, 1, 101),
  (2, 1, 'M', 60, 0, 1, 2, NULL),
  (3, 2, 'L', 100, 0, 8, 1, NULL);
INSERT INTO dd_stock_production_products (production_id, product_id) VALUES (1, 50);
INSERT INTO dd_stock_production_categories (production_id, oc_category_id, name_filter, match_exact) VALUES (1, 20, 'Лілія', 0);
INSERT INTO dd_stock_production_components (component_id, production_id, name, sort_order) VALUES
  (1, 1, 'Перед', 1), (2, 1, 'Спинка', 2);
INSERT INTO dd_stock_production_component_bom (bom_id, component_id, size_id, item_id, qty_per_unit) VALUES
  (1, 1, NULL, 2, 1.5), (2, 1, 2, 2, 2), (3, 2, NULL, 2, 1);
INSERT INTO dd_stock_production_component_stock (component_id, size_id, stock) VALUES (1, 1, 2), (2, 1, 1);
INSERT INTO dd_stock_production_sewn_bom (bom_id, production_id, size_id, component_id, item_id, qty_per_unit) VALUES
  (1, 1, NULL, 1, NULL, 1), (2, 1, NULL, 2, NULL, 1), (3, 1, NULL, NULL, 4, 1);
