import { describe, expect, it } from 'vitest';

import {
  buildOptions,
  validateAvatar,
  validateProfile,
  withoutPrimary,
} from '../miniprogram/utils/profile-edit';

describe('profile edit helpers', () => {
  it('removes the primary position from secondary selections', () => {
    expect(withoutPrimary([0, 1, 2, 3], 2)).toEqual([0, 1, 3]);
  });

  it('builds secondary options with checked and disabled state', () => {
    expect(buildOptions(1, [0, 2])).toEqual([
      { code: 0, label: '门将', checked: true, disabled: false },
      { code: 1, label: '后卫', checked: false, disabled: true },
      { code: 2, label: '中场', checked: true, disabled: false },
      { code: 3, label: '前锋', checked: false, disabled: false },
    ]);
  });

  it('validates avatar size and extension', () => {
    expect(validateAvatar(1024, '/tmp/a.jpg')).toBe('');
    expect(validateAvatar(3 * 1024 * 1024, '/tmp/a.jpg')).toBe('头像不能超过 2MB');
    expect(validateAvatar(1024, '/tmp/a.gif')).toBe('头像仅支持 JPG/PNG/WebP');
    expect(validateAvatar(1024, '/tmp/avatar')).toBe('');
  });

  it('validates profile name and region', () => {
    expect(validateProfile('球员', '上海')).toBe('');
    expect(validateProfile('', '上海')).toBe('请输入姓名');
    expect(validateProfile('a'.repeat(33), '上海')).toBe('姓名最多 32 个字符');
    expect(validateProfile('球员', 'a'.repeat(65))).toBe('地区最多 64 个字符');
  });
});
