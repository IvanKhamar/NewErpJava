-- Movements for the stock reports, after stock.sql and production.sql. Rows of 60 days ago fall outside a 30-day report.
INSERT INTO dd_stock_log (item_id, item_name, action, quantity, stock_before, stock_after, comment, date_added) VALUES
  (2, 'Бавовна', 'use', 15, 68, 53, 'Крій', NOW() - INTERVAL 1 DAY),
  (2, 'Бавовна', 'use', 5, 53, 48, 'Замовлення #7', NOW() - INTERVAL 2 DAY),
  (2, 'Бавовна', 'restock', 20, 33, 53, 'Прихід за замовленням постачальнику №1', NOW() - INTERVAL 3 DAY),
  (2, 'Бавовна', 'use', 3, 53, 50, 'Інвентаризація', NOW() - INTERVAL 1 HOUR),
  (2, 'Бавовна', 'use', 100, 150, 50, 'Крій', NOW() - INTERVAL 60 DAY),
  (1, 'Льон білий', 'use', 6, 9, 3, 'Крій', NOW() - INTERVAL 1 DAY),
  (5, 'Старе', 'use', 1, 1, 0, '', NOW() - INTERVAL 1 DAY);
INSERT INTO dd_stock_production_log (production_id, size_id, size_name, stage, direction, quantity, stock_before, stock_after,
                                     comment, date_added) VALUES
  (1, 1, 'S', 'cut', 'in', 5, 0, 5, 'Крій', NOW() - INTERVAL 5 DAY),
  (1, 1, 'S', 'cut', 'out', 3, 5, 2, 'В пошив', NOW() - INTERVAL 4 DAY),
  (1, 1, 'S', 'sewn', 'in', 3, 0, 3, '', NOW() - INTERVAL 3 DAY),
  (1, 1, 'S', 'sewn', 'out', 2, 3, 1, 'Замовлення #7', NOW() - INTERVAL 2 DAY),
  (1, 1, 'S', 'sewn', 'in', 1, 1, 2, 'Скасування #7', NOW() - INTERVAL 2 DAY),
  (1, 1, 'S', 'sewn', 'out', 1, 2, 1, 'Фотосесія', NOW() - INTERVAL 1 DAY),
  (1, 1, 'S', 'sewn', 'in', 2, 1, 3, 'Інвентаризація', NOW() - INTERVAL 1 HOUR),
  (1, 1, 'S-old', 'cut', 'out', 1, 3, 2, 'Інвентаризація: брак', NOW() - INTERVAL 1 HOUR),
  (1, 2, 'M', 'cut', 'in', 4, 0, 4, 'Крій', NOW() - INTERVAL 60 DAY),
  (2, 3, 'L', 'sewn', 'in', 10, 2, 12, 'Прихід', NOW() - INTERVAL 2 DAY),
  (2, 3, 'L', 'sewn', 'out', 4, 12, 8, 'Замовлення #8', NOW() - INTERVAL 1 DAY);
