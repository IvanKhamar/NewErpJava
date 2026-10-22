-- Invented consumables for the stock tests (run after users.sql).
DELETE FROM erp_purchase_order_line; DELETE FROM erp_purchase_order; DELETE FROM dd_stock_log;
DELETE FROM dd_stock_items; DELETE FROM dd_stock_categories; DELETE FROM dd_stock_suppliers;

INSERT INTO dd_stock_categories (category_id, name, sort_order) VALUES (1, 'Тканини', 1), (2, 'Фурнітура', 2);
INSERT INTO dd_stock_suppliers (supplier_id, name, phone, email, categories, notes) VALUES
  (1, 'ТексМаркет', '+380441112233', 'tex@example.com', 'тканини', ''),
  (2, 'Ґудзик і Ко', '', '', 'фурнітура', '');
INSERT INTO dd_stock_items (item_id, name, category_id, unit, stock, min_stock, used_for, supplier_id, price, status) VALUES
  (1, 'Льон білий', 1, 'м', 3, 10, 'сукні', 1, 200, 1),
  (2, 'Бавовна', 1, 'м', 50, 10, '', 1, 120, 1),
  (3, 'Ґудзики 15 мм', 2, 'шт', 20, 100, '', 2, 1.5, 1),
  (4, 'Нитки', NULL, 'шт', 0, 5, '', NULL, 30, 1),
  (5, 'Старе', 1, 'м', 0, 10, '', 1, 1, 0);
