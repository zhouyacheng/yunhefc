ALTER TABLE app_user
  MODIFY COLUMN phone VARCHAR(20) NULL COMMENT '登录手机号；纯微信注册账号可为空';
