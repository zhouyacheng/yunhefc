-- 首位管理员一次性引导脚本（生产空库 Flyway 完成后执行）。
--
-- 执行前：
--   1. 修改 @phone / @name / @position；
--   2. 用应用同款 BCryptPasswordEncoder 生成哈希，替换 @password_hash 的占位符；
--      示例：htpasswd -bnBC 10 "" '<pwd>' | tr -d ':\n' | sed 's/^\$2y\$/$2a$/'
--   3. 执行后用 mysql 客户端核对末尾输出的 admin_permissions 是否为 28。
--
-- 重复执行是安全的：已有账号只恢复 status/is_deleted，不重置密码；
-- 角色与球员档案按唯一键恢复 is_deleted=0。脚本按 phone 与角色 code 查 ID，不硬编码。
-- 生产空库不得执行 test-data.sql。

SET @phone = '13800000000';
SET @name = '管理员';
SET @password_hash = '<REPLACE_WITH_BCRYPT_HASH>';
SET @position = 2; -- 0-门将;1-后卫;2-中场;3-前锋

SET @team_id = (SELECT id FROM team WHERE is_deleted = 0 ORDER BY id LIMIT 1);

INSERT INTO app_user (phone, password_hash, status)
VALUES (@phone, @password_hash, 1)
ON DUPLICATE KEY UPDATE status = 1, is_deleted = 0;

SET @user_id = (SELECT id FROM app_user WHERE phone = @phone AND is_deleted = 0 LIMIT 1);

INSERT INTO user_role (user_id, role_id)
SELECT @user_id, r.id
FROM app_role r
WHERE r.team_id = @team_id AND r.code = 'ADMIN' AND r.is_deleted = 0
ON DUPLICATE KEY UPDATE is_deleted = 0;

-- jersey 留空，避开正式球员号码唯一键；type=1 为正式球员。
INSERT INTO player (team_id, user_id, name, jersey, type, position, skill)
VALUES (@team_id, @user_id, @name, NULL, 1, @position, 3)
ON DUPLICATE KEY UPDATE is_deleted = 0;

SELECT @user_id AS user_id, @team_id AS team_id;

SELECT COUNT(*) AS admin_permissions
FROM user_role ur
JOIN role_permission rp ON rp.role_id = ur.role_id AND rp.is_deleted = 0
JOIN app_role r ON r.id = ur.role_id AND r.is_deleted = 0
WHERE ur.user_id = @user_id AND ur.is_deleted = 0 AND r.code = 'ADMIN';
