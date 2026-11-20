-- P-74：微信登录改为小程序登录，使用独立 mp_openid。
-- 小程序 openid 与公众号 openid 属于不同 AppID 命名空间，不能复用；不迁移存量数据。
-- 旧 openid 与 uk_user_openid 保留为历史只读，待备份并显式批准后由后续版本删除。
ALTER TABLE app_user
  ADD COLUMN mp_openid VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL
    COMMENT '微信小程序openid;唯一;大小写敏感' AFTER openid,
  ADD UNIQUE KEY uk_user_mp_openid (mp_openid);
