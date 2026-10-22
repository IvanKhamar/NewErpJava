-- Invented Nova Poshta directory and shipping data (run after orders.sql).
DELETE FROM dd_novaposhta_warehouse; DELETE FROM erp_shipment_sender;

INSERT INTO dd_novaposhta_warehouse (ref, description, category_of_warehouse, city_ref, city_description, weight, sort_order, status) VALUES
  ('wh-kyiv-10', 'Відділення №10: вул. Велика Васильківська, 5', 'Branch', 'city-kyiv', 'Київ', 30, 10, 1),
  ('wh-kyiv-2', 'Відділення №2: вул. Хрещатик, 1', 'Branch', 'city-kyiv', 'Київ', 1000, 2, 1),
  ('wh-kyiv-closed', 'Відділення №3: закрите', 'Branch', 'city-kyiv', 'Київ', 30, 3, 0),
  ('wh-kyivska-1', 'Відділення №1', 'Branch', 'city-kyivska', 'Київська Слобода', 30, 1, 1),
  ('wh-lviv-1', 'Поштомат №1', 'Postomat', 'city-lviv', 'Львів', 20, 1, 1);

UPDATE dd_order SET shipping_address_2 = 'Відділення №2: вул. Хрещатик, 1', payment_code = 'cod' WHERE order_id = 1001;
