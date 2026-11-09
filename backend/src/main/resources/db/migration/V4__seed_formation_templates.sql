-- 8 套预置阵型与 64 个槽位；坐标与槽位标识以原型预置阵型为参照。

INSERT INTO formation_template (team_id, name, is_preset, base_template_id, created_by)
VALUES
    (NULL, '2-3-2', 1, NULL, NULL),
    (NULL, '3-2-2', 1, NULL, NULL),
    (NULL, '3-3-1', 1, NULL, NULL),
    (NULL, '2-2-3', 1, NULL, NULL),
    (NULL, '3-1-3', 1, NULL, NULL),
    (NULL, '4-1-2', 1, NULL, NULL),
    (NULL, '4-2-1', 1, NULL, NULL),
    (NULL, '4-0-3', 1, NULL, NULL);

INSERT IGNORE INTO formation_slot (template_id, slot_key, role, x, y, sort_order)
SELECT t.id, s.slot_key, s.slot_role, s.slot_x, s.slot_y, s.slot_order
FROM formation_template t
JOIN (
    SELECT '2-3-2' AS template_name, 'gk' AS slot_key, 0 AS slot_role, 50.00 AS slot_x, 88.00 AS slot_y, 0 AS slot_order
    UNION ALL SELECT '2-3-2', 'd1', 1, 25.00, 70.00, 1
    UNION ALL SELECT '2-3-2', 'd2', 1, 75.00, 70.00, 2
    UNION ALL SELECT '2-3-2', 'm1', 2, 18.00, 50.00, 3
    UNION ALL SELECT '2-3-2', 'm2', 2, 50.00, 50.00, 4
    UNION ALL SELECT '2-3-2', 'm3', 2, 82.00, 50.00, 5
    UNION ALL SELECT '2-3-2', 'f1', 3, 35.00, 24.00, 6
    UNION ALL SELECT '2-3-2', 'f2', 3, 65.00, 24.00, 7
    UNION ALL SELECT '3-2-2', 'gk', 0, 50.00, 88.00, 0
    UNION ALL SELECT '3-2-2', 'd1', 1, 20.00, 70.00, 1
    UNION ALL SELECT '3-2-2', 'd2', 1, 50.00, 70.00, 2
    UNION ALL SELECT '3-2-2', 'd3', 1, 80.00, 70.00, 3
    UNION ALL SELECT '3-2-2', 'm1', 2, 35.00, 50.00, 4
    UNION ALL SELECT '3-2-2', 'm2', 2, 65.00, 50.00, 5
    UNION ALL SELECT '3-2-2', 'f1', 3, 35.00, 24.00, 6
    UNION ALL SELECT '3-2-2', 'f2', 3, 65.00, 24.00, 7
    UNION ALL SELECT '3-3-1', 'gk', 0, 50.00, 88.00, 0
    UNION ALL SELECT '3-3-1', 'd1', 1, 20.00, 70.00, 1
    UNION ALL SELECT '3-3-1', 'd2', 1, 50.00, 70.00, 2
    UNION ALL SELECT '3-3-1', 'd3', 1, 80.00, 70.00, 3
    UNION ALL SELECT '3-3-1', 'm1', 2, 20.00, 50.00, 4
    UNION ALL SELECT '3-3-1', 'm2', 2, 50.00, 50.00, 5
    UNION ALL SELECT '3-3-1', 'm3', 2, 80.00, 50.00, 6
    UNION ALL SELECT '3-3-1', 'f1', 3, 50.00, 24.00, 7
    UNION ALL SELECT '2-2-3', 'gk', 0, 50.00, 88.00, 0
    UNION ALL SELECT '2-2-3', 'd1', 1, 30.00, 70.00, 1
    UNION ALL SELECT '2-2-3', 'd2', 1, 70.00, 70.00, 2
    UNION ALL SELECT '2-2-3', 'm1', 2, 30.00, 50.00, 3
    UNION ALL SELECT '2-2-3', 'm2', 2, 70.00, 50.00, 4
    UNION ALL SELECT '2-2-3', 'f1', 3, 20.00, 24.00, 5
    UNION ALL SELECT '2-2-3', 'f2', 3, 50.00, 24.00, 6
    UNION ALL SELECT '2-2-3', 'f3', 3, 80.00, 24.00, 7
    UNION ALL SELECT '3-1-3', 'gk', 0, 50.00, 88.00, 0
    UNION ALL SELECT '3-1-3', 'd1', 1, 20.00, 70.00, 1
    UNION ALL SELECT '3-1-3', 'd2', 1, 50.00, 70.00, 2
    UNION ALL SELECT '3-1-3', 'd3', 1, 80.00, 70.00, 3
    UNION ALL SELECT '3-1-3', 'm1', 2, 50.00, 50.00, 4
    UNION ALL SELECT '3-1-3', 'f1', 3, 20.00, 24.00, 5
    UNION ALL SELECT '3-1-3', 'f2', 3, 50.00, 24.00, 6
    UNION ALL SELECT '3-1-3', 'f3', 3, 80.00, 24.00, 7
    UNION ALL SELECT '4-1-2', 'gk', 0, 50.00, 88.00, 0
    UNION ALL SELECT '4-1-2', 'd1', 1, 15.00, 70.00, 1
    UNION ALL SELECT '4-1-2', 'd2', 1, 38.00, 70.00, 2
    UNION ALL SELECT '4-1-2', 'd3', 1, 62.00, 70.00, 3
    UNION ALL SELECT '4-1-2', 'd4', 1, 85.00, 70.00, 4
    UNION ALL SELECT '4-1-2', 'm1', 2, 50.00, 50.00, 5
    UNION ALL SELECT '4-1-2', 'f1', 3, 35.00, 24.00, 6
    UNION ALL SELECT '4-1-2', 'f2', 3, 65.00, 24.00, 7
    UNION ALL SELECT '4-2-1', 'gk', 0, 50.00, 88.00, 0
    UNION ALL SELECT '4-2-1', 'd1', 1, 15.00, 70.00, 1
    UNION ALL SELECT '4-2-1', 'd2', 1, 38.00, 70.00, 2
    UNION ALL SELECT '4-2-1', 'd3', 1, 62.00, 70.00, 3
    UNION ALL SELECT '4-2-1', 'd4', 1, 85.00, 70.00, 4
    UNION ALL SELECT '4-2-1', 'm1', 2, 35.00, 50.00, 5
    UNION ALL SELECT '4-2-1', 'm2', 2, 65.00, 50.00, 6
    UNION ALL SELECT '4-2-1', 'f1', 3, 50.00, 24.00, 7
    UNION ALL SELECT '4-0-3', 'gk', 0, 50.00, 88.00, 0
    UNION ALL SELECT '4-0-3', 'd1', 1, 15.00, 70.00, 1
    UNION ALL SELECT '4-0-3', 'd2', 1, 38.00, 70.00, 2
    UNION ALL SELECT '4-0-3', 'd3', 1, 62.00, 70.00, 3
    UNION ALL SELECT '4-0-3', 'd4', 1, 85.00, 70.00, 4
    UNION ALL SELECT '4-0-3', 'f1', 3, 20.00, 24.00, 5
    UNION ALL SELECT '4-0-3', 'f2', 3, 50.00, 24.00, 6
    UNION ALL SELECT '4-0-3', 'f3', 3, 80.00, 24.00, 7
) s ON s.template_name = t.name
WHERE t.is_preset = 1 AND t.team_id IS NULL;
