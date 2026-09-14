-- 应用市场：appmarket_app.icon_url 扩为 TEXT
--
-- 背景
--   为支持「从 APK 导入」写入解析出的图标（base64 data URI），
--   App.iconUrl 已改为 @Column(columnDefinition = "TEXT")。
--
-- 重要
--   spring.jpa.hibernate.ddl-auto=update 只会新增列，不会修改已有列的类型，
--   因此本脚本必须手动执行；否则写入 data URI 会被 varchar(255) 截断，
--   导致图标损坏（表现为门户图片无法渲染）。
--
-- 执行方式
--   按目标数据库选择对应语句执行，两种数据库的语句不可互换。
--   本变更只放宽列长度（varchar -> TEXT），不破坏既有 URL 型图标数据，无数据迁移风险。

-- MySQL / MariaDB（生产环境）
ALTER TABLE appmarket_app MODIFY COLUMN icon_url TEXT;

-- H2（本地冒烟，ddl-auto=create 时无需执行）
-- ALTER TABLE appmarket_app ALTER COLUMN icon_url TEXT;
