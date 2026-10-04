-- 演示数据：4 个馆（含 1 个远端分馆）、4 种书、13 个条码、6 位读者
-- 测试账号即读者证号：P0001 ~ P0006；馆员操作通过 X-Operator 请求头传工号（如 STAFF01）

INSERT INTO library (code, name, district, remote) VALUES
 ('CENTRAL', '市中心图书馆', '市中心', FALSE),
 ('EAST',    '城东分馆',     '城东',   FALSE),
 ('WEST',    '城西分馆',     '城西',   FALSE),
 ('NORTH',   '远郊北峰分馆', '北峰山区', TRUE);

INSERT INTO book (isbn, title, author, publisher, publish_year, ebook_url, ebook_available, price) VALUES
 ('9787536692930', '三体',       '刘慈欣',   '重庆出版社', 2008, 'https://ebook.example.org/santi',     TRUE,  59.00),
 ('9787544253994', '百年孤独',   '加西亚·马尔克斯', '南海出版公司', 2011, NULL, FALSE, 39.50),
 ('9787508647357', '人类简史',   '尤瓦尔·赫拉利',   '中信出版社', 2014, 'https://ebook.example.org/sapiens',   TRUE,  68.00),
 ('9787020024759', '围城',       '钱锺书',   '人民文学出版社', 1991, NULL, FALSE, 42.00);

-- 《三体》：中心馆仅 1 本（制造排队），城东 4 本（普通馆可调拨来源），
-- 城西 2 本，远郊北峰 1 本（远端馆孤本，受留存比例保护不可调出）
INSERT INTO book_copy (book_id, barcode, owning_library_id, current_library_id, status)
SELECT b.id, v.barcode, l1.id, l2.id, 'AVAILABLE'
FROM (VALUES
  ('9787536692930', 'BC-1001', 'CENTRAL', 'CENTRAL'),
  ('9787536692930', 'BC-1002', 'EAST',    'EAST'),
  ('9787536692930', 'BC-1003', 'EAST',    'EAST'),
  ('9787536692930', 'BC-1004', 'EAST',    'EAST'),
  ('9787536692930', 'BC-1005', 'EAST',    'EAST'),
  ('9787536692930', 'BC-1006', 'WEST',    'WEST'),
  ('9787536692930', 'BC-1007', 'WEST',    'WEST'),
  ('9787536692930', 'BC-1008', 'NORTH',   'NORTH'),
  ('9787544253994', 'BC-2001', 'CENTRAL', 'CENTRAL'),
  ('9787544253994', 'BC-2002', 'CENTRAL', 'CENTRAL'),
  ('9787544253994', 'BC-2003', 'CENTRAL', 'CENTRAL'),
  ('9787508647357', 'BC-3001', 'CENTRAL', 'CENTRAL'),
  ('9787508647357', 'BC-3002', 'EAST',    'EAST'),
  ('9787020024759', 'BC-4001', 'NORTH',   'NORTH'),
  ('9787020024759', 'BC-4002', 'EAST',    'EAST')
) AS v(isbn, barcode, owner, cur)
JOIN book b ON b.isbn = v.isbn
JOIN library l1 ON l1.code = v.owner
JOIN library l2 ON l2.code = v.cur;

INSERT INTO patron (card_no, name, email, status) VALUES
 ('P0001', '张伟', 'zhangwei@example.org',  'ACTIVE'),
 ('P0002', '李娜', 'lina@example.org',      'ACTIVE'),
 ('P0003', '王芳', 'wangfang@example.org',  'ACTIVE'),
 ('P0004', '陈杰', 'chenjie@example.org',   'ACTIVE'),
 ('P0005', '赵敏', 'zhaomin@example.org',   'ACTIVE'),
 ('P0006', '孙强', 'sunqiang@example.org',  'ACTIVE');
