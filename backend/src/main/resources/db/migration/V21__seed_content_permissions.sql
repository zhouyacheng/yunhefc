-- 内容（动态与相册，P-142）：4 个权限码与角色授权，权限码总数 33 -> 37。
-- V3 已应用不可改；角色授权按权限 code 关联，不依赖固定角色 ID。

SET @team_id = (SELECT id FROM team WHERE name = '云河FC' LIMIT 1);

INSERT INTO permission (code, name, resource, action, scope)
VALUES
    ('content:create:own', '创建相册、上传与删除本人照片、发布与删除本人动态、点赞', 'content', 'create', 'own'),
    ('content:delete:any', '删除任意动态/照片、接收举报通知', 'content', 'delete', 'any'),
    ('content:manage:any', '编辑与删除任意相册（名称、封面、归属）', 'content', 'manage', 'any'),
    ('content:view:any', '浏览动态与相册、举报内容', 'content', 'view', 'any')
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    resource = VALUES(resource),
    action = VALUES(action),
    scope = VALUES(scope);

INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM app_role r
JOIN permission p ON p.code IN (
    'content:create:own', 'content:delete:any', 'content:manage:any', 'content:view:any'
)
WHERE r.team_id = @team_id AND r.code = 'ADMIN';

INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM app_role r
JOIN permission p ON p.code IN (
    'content:create:own', 'content:view:any'
)
WHERE r.team_id = @team_id AND r.code = 'PLAYER';
