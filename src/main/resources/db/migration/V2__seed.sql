-- 默认规则参数（可通过管理端政策接口调整，人工操作记录规则依据）
INSERT INTO policies (pk, numeric_value, text_value, description) VALUES
  ('loan.periodDays',              30,   NULL, '标准借阅期限（天）'),
  ('loan.renewLimit',              1,    NULL, '无排队冲突时最大续借次数（热门书不得被长期占用）'),
  ('hold.pickupDays',              3,    NULL, '到馆后取书时限（天），超时释放给下一位'),
  ('hold.maxPerReader',            10,   NULL, '单读者同时有效预约数上限'),
  ('fine.daily',                   0.50, NULL, '逾期罚款单价（元/天）'),
  ('fine.cap',                     20.00,NULL, '单册逾期罚款上限（元）'),
  ('fee.damage',                   10.00,NULL, '污损赔偿（元）'),
  ('fee.missingPages',             20.00,NULL, '缺页赔偿（元）'),
  ('fee.accessory',                5.00, NULL, '缺附件赔偿（元）'),
  ('fee.replacement',              50.00,NULL, '丢书赔书价（元）'),
  ('fee.logisticsLoss',            60.00,NULL, '物流丢包赔付馆方金额（元）'),
  ('transfer.sameRegionHours',     24,   NULL, '同片区调拨预计在途小时'),
  ('transfer.otherRegionHours',    72,   NULL, '跨片区调拨预计在途小时'),
  ('transfer.protectionRatio',     0.5,  NULL, '外馆保护比例：调拨后本馆可借留存不得低于该比例的可借副本（防止远端分馆被抽空）'),
  ('closure.graceHours',           24,   NULL, '临时闭馆时取书/还书期限自动顺延小时');

-- 分馆（x/y 为城市坐标，用于物流时效估算）
INSERT INTO libraries (code, name, region, x, y) VALUES
  ('CENTRAL', '中心图书馆', 'CENTRAL', 0.0, 0.0),
  ('NORTH',   '城北分馆',   'NORTH',   8.0, 6.0),
  ('SOUTH',   '城南分馆',   'SOUTH',   7.0, -7.0),
  ('WEST',    '城西分馆',   'WEST',    -9.0, 2.0),
  ('EAST',    '城东远端分馆','EAST',   20.0, 1.0);

-- 读者（测试账号）
INSERT INTO readers (card_no, name, email, status) VALUES
  ('R001', '张三', 'zhangsan@example.com', 'ACTIVE'),
  ('R002', '李四', 'lisi@example.com',     'ACTIVE'),
  ('R003', '王五', 'wangwu@example.com',   'ACTIVE');

-- 书目
INSERT INTO bibs (isbn, title, author, subject, ebook_available, ebook_url) VALUES
  ('9787536692930', '三体',       '刘慈欣', '科幻', TRUE,  'https://e.example.com/santi'),
  ('9787020028696', '红楼梦',     '曹雪芹', '文学', FALSE, NULL),
  ('9787544253994', '解忧杂货店', '东野圭吾','推理', TRUE, 'https://e.example.com/jieyou'),
  ('9787020042485', '平凡的世界', '路遥',   '文学', FALSE, NULL);

-- 馆藏条码（home/current 一致为初始状态）
INSERT INTO items (barcode, bib_id, home_library_id, current_library_id, status, cond) VALUES
  ('C-0001', 1, 1, 1, 'AVAILABLE', 'GOOD'),
  ('C-0002', 1, 2, 2, 'AVAILABLE', 'GOOD'),
  ('C-0003', 1, 3, 3, 'AVAILABLE', 'GOOD'),
  ('C-0004', 1, 5, 5, 'AVAILABLE', 'GOOD'),
  ('C-0005', 1, 5, 5, 'AVAILABLE', 'GOOD'),
  ('C-0006', 1, 5, 5, 'AVAILABLE', 'GOOD'),
  ('H-0001', 2, 1, 1, 'AVAILABLE', 'GOOD'),
  ('H-0002', 2, 2, 2, 'AVAILABLE', 'GOOD'),
  ('J-0001', 3, 1, 1, 'AVAILABLE', 'GOOD'),
  ('J-0002', 3, 4, 4, 'AVAILABLE', 'GOOD'),
  ('P-0001', 4, 2, 2, 'AVAILABLE', 'GOOD');
