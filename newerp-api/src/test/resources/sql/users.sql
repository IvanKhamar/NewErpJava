-- Invented users. Every NewErp login has the password Passw0rd! (hash in the ASP.NET Identity v3 format).
DELETE FROM dd_user; DELETE FROM erp_user_login;
INSERT INTO dd_user (user_id, user_group_id, username, password, salt, firstname, lastname, email, image, code, ip, status, date_added) VALUES
  (1, 1, 'admin', '', '', 'Адмін', 'Головний', 'admin@example.com', '', '', '', 1, '2026-01-01'),
  (2, 1, 'keeper', '', '', 'Галина', 'Комірник', '', '', '', '', 1, '2026-01-01'),
  (7, 1, 'olena', '', '', 'Олена', 'Коваль', '', '', '', '', 1, '2026-01-01'),
  (8, 1, 'andrii', '', '', 'Андрій', 'Бондар', '', '', '', '', 1, '2026-01-01'),
  (9, 1, 'old', '', '', 'Ярема', 'Старий', '', '', '', '', 0, '2026-01-01');
INSERT INTO erp_user_login (user_id, password_hash, role, security_stamp, must_change_password) VALUES
  (1, 'AQAAAAIAAYagAAAAEAABAgMEBQYHCAkKCwwNDg9tHDBVNhTVA+6E1t6Gpb7bZypsIX6UNUiGvKx4ddfq6Q==', 'Admin', 'stamp1', 0),
  (2, 'AQAAAAIAAYagAAAAEAABAgMEBQYHCAkKCwwNDg9tHDBVNhTVA+6E1t6Gpb7bZypsIX6UNUiGvKx4ddfq6Q==', 'Storekeeper', 'stamp2', 0),
  (7, 'AQAAAAIAAYagAAAAEAABAgMEBQYHCAkKCwwNDg9tHDBVNhTVA+6E1t6Gpb7bZypsIX6UNUiGvKx4ddfq6Q==', 'Manager', 'stamp7', 0),
  (9, 'AQAAAAIAAYagAAAAEAABAgMEBQYHCAkKCwwNDg9tHDBVNhTVA+6E1t6Gpb7bZypsIX6UNUiGvKx4ddfq6Q==', 'Manager', 'stamp9', 0);
