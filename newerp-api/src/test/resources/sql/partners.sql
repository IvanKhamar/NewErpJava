-- For the partner feed (run after catalog.sql): the price of size M, the main and an extra photo of product 60, the category name.
DELETE FROM dd_partner; DELETE FROM dd_partner_category_map; DELETE FROM dd_partner_product_map;
DELETE FROM dd_product_image; DELETE FROM dd_category_description;
UPDATE dd_product_option_value SET price = 900 WHERE product_id = 60;
UPDATE dd_product SET image = 'catalog/main.jpg' WHERE product_id = 60;
INSERT INTO dd_product_image (product_id, image, sort_order) VALUES (60, 'catalog/зад.jpg', 1);
INSERT INTO dd_category_description (category_id, language_id, name, description, meta_title, meta_description, meta_keyword) VALUES
  (30, 3, 'Сорочки', '', '', '', '');
