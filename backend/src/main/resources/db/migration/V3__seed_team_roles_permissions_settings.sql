-- 球队、默认角色、28 个权限码与 9 项系统配置。
-- 角色授权按 05 §5.1 的 P-01 已确认内容。

INSERT INTO team (id, name, short_name)
VALUES (1, '云河FC', '云河FC')
ON DUPLICATE KEY UPDATE name = VALUES(name), short_name = VALUES(short_name);

SET @team_id = (SELECT id FROM team WHERE name = '云河FC' LIMIT 1);

INSERT INTO app_role (team_id, code, name, is_default)
VALUES
    (@team_id, 'GUEST', '外挂球员', 1),
    (@team_id, 'PLAYER', '正式球员', 1),
    (@team_id, 'ADMIN', '管理员', 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), is_default = VALUES(is_default);

INSERT INTO permission (code, name, resource, action, scope)
VALUES
    ('attendance:checkin:own', '自助签到', 'attendance', 'checkin', 'own'),
    ('attendance:mark:any', '管理员标记/修正考勤、补录临时到场', 'attendance', 'mark', 'any'),
    ('attendance:view:any', '查看全队报名与考勤', 'attendance', 'view', 'any'),
    ('audit:view:any', '查看操作日志', 'audit', 'view', 'any'),
    ('event:create:own', '创建自己的事件', 'event', 'create', 'own'),
    ('event:delete:any', '删除事件', 'event', 'delete', 'any'),
    ('event:edit:any', '修改任意事件、录入/修正比分', 'event', 'edit', 'any'),
    ('event:edit:own', '修改自己的事件', 'event', 'edit', 'own'),
    ('grouping:generate:any', '生成/调整/保存/清除分组', 'grouping', 'generate', 'any'),
    ('grouping:view:any', '查看分组', 'grouping', 'view', 'any'),
    ('invite:create:match', '创建比赛邀请', 'invite', 'create', 'match'),
    ('invite:manage:any', '管理入队/比赛邀请', 'invite', 'manage', 'any'),
    ('lineup:edit:any', '编辑阵容与阵型模板', 'lineup', 'edit', 'any'),
    ('lineup:view:any', '查看阵容', 'lineup', 'view', 'any'),
    ('match:create:any', '创建比赛', 'match', 'create', 'any'),
    ('match:edit:any', '编辑/延期/重新排期比赛', 'match', 'edit', 'any'),
    ('match:status:any', '比赛状态变更/取消', 'match', 'status', 'any'),
    ('match:view:any', '管理端查看比赛', 'match', 'view', 'any'),
    ('player:edit:any', '管理球员档案、换绑手机、解绑微信、代传头像', 'player', 'edit', 'any'),
    ('player:edit:own', '自助编辑资料与头像', 'player', 'edit', 'own'),
    ('player:view:any', '查看管理端球员列表', 'player', 'view', 'any'),
    ('registration:create:own', '报名/取消报名', 'registration', 'create', 'own'),
    ('registration:remove:any', '管理员移除报名', 'registration', 'remove', 'any'),
    ('role:manage:any', '角色 CRUD、权限分配、用户角色分配', 'role', 'manage', 'any'),
    ('role:view:any', '查看角色与权限目录', 'role', 'view', 'any'),
    ('settings:manage:any', '修改系统设置', 'settings', 'manage', 'any'),
    ('settings:view:any', '查看系统设置', 'settings', 'view', 'any'),
    ('user:approve:any', '审核通过待审核用户', 'user', 'approve', 'any')
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    resource = VALUES(resource),
    action = VALUES(action),
    scope = VALUES(scope);

INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM app_role r
JOIN permission p ON p.code IN (
    'attendance:checkin:own', 'attendance:mark:any', 'attendance:view:any',
    'audit:view:any', 'event:create:own', 'event:delete:any', 'event:edit:any',
    'event:edit:own', 'grouping:generate:any', 'grouping:view:any',
    'invite:create:match', 'invite:manage:any', 'lineup:edit:any', 'lineup:view:any',
    'match:create:any', 'match:edit:any', 'match:status:any', 'match:view:any',
    'player:edit:any', 'player:edit:own', 'player:view:any',
    'registration:create:own', 'registration:remove:any',
    'role:manage:any', 'role:view:any', 'settings:manage:any', 'settings:view:any',
    'user:approve:any'
)
WHERE r.team_id = @team_id AND r.code = 'ADMIN';

INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM app_role r
JOIN permission p ON p.code IN (
    'player:edit:own', 'registration:create:own', 'attendance:checkin:own',
    'attendance:view:any', 'lineup:view:any', 'grouping:view:any',
    'event:create:own', 'event:edit:own', 'invite:create:match'
)
WHERE r.team_id = @team_id AND r.code = 'PLAYER';

INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM app_role r
JOIN permission p ON p.code IN (
    'player:edit:own', 'registration:create:own', 'attendance:checkin:own'
)
WHERE r.team_id = @team_id AND r.code = 'GUEST';

INSERT INTO system_setting (team_id, setting_key, setting_value, value_type)
VALUES
    (@team_id, 'registration.maxPlayers', JSON_EXTRACT('30', '$'), 'NUMBER'),
    (@team_id, 'registration.maxGuests', JSON_EXTRACT('4', '$'), 'NUMBER'),
    (@team_id, 'registration.maxWaitlist', JSON_EXTRACT('2', '$'), 'NUMBER'),
    (@team_id, 'grouping.manualWeight', JSON_EXTRACT('70', '$'), 'NUMBER'),
    (@team_id, 'grouping.autoWeight', JSON_EXTRACT('30', '$'), 'NUMBER'),
    (@team_id, 'grouping.appearanceWeight', JSON_EXTRACT('50', '$'), 'NUMBER'),
    (@team_id, 'grouping.goalWeight', JSON_EXTRACT('25', '$'), 'NUMBER'),
    (@team_id, 'grouping.assistWeight', JSON_EXTRACT('25', '$'), 'NUMBER'),
    (@team_id, 'grouping.minAppearances', JSON_EXTRACT('3', '$'), 'NUMBER')
ON DUPLICATE KEY UPDATE
    setting_value = VALUES(setting_value),
    value_type = VALUES(value_type);
