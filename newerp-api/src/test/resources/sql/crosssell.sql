-- Category 30 «Сукні» for the cross-sell panel (run after product-sort.sql); the panel starts empty.
DELETE FROM dd_category; DELETE FROM dd_crosssell_item;
INSERT INTO dd_category (category_id, image, parent_id, top, `column`, sort_order, status, date_added, date_modified, production_period) VALUES
  (30, NULL, 0, 0, 1, 0, 1, '2026-01-01 00:00:00', '2026-01-01 00:00:00', 0);
