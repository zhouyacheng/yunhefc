-- 队内活动与账本（P-92~P-94）：5 个权限码、角色授权与 7 个内置账本分类。
-- V3 已应用不可改；角色授权按权限 code 关联，不依赖固定角色 ID。

SET @team_id = (SELECT id FROM team WHERE name = '云河FC' LIMIT 1);

INSERT INTO permission (code, name, resource, action, scope)
VALUES
    ('activity:manage:any', '创建/编辑活动、状态流转、移除报名、上传地点图片', 'activity', 'manage', 'any'),
    ('activity:signup:own', '报名/取消本人活动报名', 'activity', 'signup', 'own'),
    ('activity:view:any', '查看活动报名名单', 'activity', 'view', 'any'),
    ('ledger:manage:any', '维护账本流水与分类', 'ledger', 'manage', 'any'),
    ('ledger:view:any', '查看账本、汇总与分类', 'ledger', 'view', 'any')
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    resource = VALUES(resource),
    action = VALUES(action),
    scope = VALUES(scope);

INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM app_role r
JOIN permission p ON p.code IN (
    'activity:manage:any', 'activity:signup:own', 'activity:view:any',
    'ledger:manage:any', 'ledger:view:any'
)
WHERE r.team_id = @team_id AND r.code = 'ADMIN';

INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM app_role r
JOIN permission p ON p.code IN (
    'activity:signup:own', 'activity:view:any', 'ledger:view:any'
)
WHERE r.team_id = @team_id AND r.code = 'PLAYER';

INSERT INTO ledger_category (team_id, name, direction, sort_no)
VALUES
    (@team_id, '队费', 0, 1),
    (@team_id, '场地', 1, 2),
    (@team_id, '装备', 1, 3),
    (@team_id, '活动', 1, 4),
    (@team_id, '餐饮', 1, 5),
    (@team_id, '赞助', 0, 6),
    (@team_id, '其他', NULL, 7)
ON DUPLICATE KEY UPDATE
    direction = VALUES(direction),
    sort_no = VALUES(sort_no);
