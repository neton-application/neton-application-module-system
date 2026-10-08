-- V002：站点品牌配置改名 site.* → system.site.*。
--
-- 设置 key 的首段必须是模块名（分类 = 模块名：member.signin.cycle_days、payment.mock.enabled），
-- 第一版写成了 site.name / site.tagline、分类 site。代码里的定义已改名，这里把库里已有的行
-- 跟着改过去 —— 不改的话，运营在后台填过的站点名会变成一行没有定义的孤儿，页面回到默认值。
--
-- 新 key 已存在（先发了新版本、又同步过缺失项）时以新行为准，旧行直接删掉。

UPDATE system_settings
   SET setting_key = 'system.site.name', category = 'system'
 WHERE setting_key = 'site.name'
   AND NOT EXISTS (SELECT 1 FROM system_settings WHERE setting_key = 'system.site.name');

UPDATE system_settings
   SET setting_key = 'system.site.tagline', category = 'system'
 WHERE setting_key = 'site.tagline'
   AND NOT EXISTS (SELECT 1 FROM system_settings WHERE setting_key = 'system.site.tagline');

DELETE FROM system_settings WHERE setting_key IN ('site.name', 'site.tagline');
