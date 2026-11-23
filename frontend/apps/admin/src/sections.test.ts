import { ADMIN_SECTIONS } from '@yunhefc/permissions';
import { describe, expect, it } from 'vitest';

import { ADMIN_NAV_GROUPS, SECTION_LABELS } from './sections';

describe('ADMIN_NAV_GROUPS', () => {
  it('按设计文档分组并保持组内顺序', () => {
    expect(
      ADMIN_NAV_GROUPS.map((group) => ({ label: group.label, sections: [...group.sections] })),
    ).toEqual([
      { label: '运营', sections: ['matches', 'registrations', 'events', 'activities', 'ledger'] },
      { label: '战术', sections: ['lineup', 'grouping'] },
      { label: '球队', sections: ['players', 'roles'] },
      { label: '系统', sections: ['invites', 'settings', 'audit'] },
    ]);
  });

  it('恰好覆盖全部 ADMIN_SECTIONS 且不重复', () => {
    const sections = ADMIN_NAV_GROUPS.flatMap((group) => group.sections);

    expect(sections).toHaveLength(ADMIN_SECTIONS.length);
    expect(new Set(sections).size).toBe(sections.length);
    expect(new Set(sections)).toEqual(new Set(ADMIN_SECTIONS));
  });

  it('每个分区的菜单标题都存在', () => {
    for (const group of ADMIN_NAV_GROUPS) {
      for (const section of group.sections) {
        expect(SECTION_LABELS[section]).toBeTruthy();
      }
    }
  });
});
