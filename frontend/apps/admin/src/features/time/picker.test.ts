import { describe, expect, it } from 'vitest';

import { SHANGHAI_OFFSET_MS, fromPickerDate, shanghaiDayAt, toPickerDate } from './picker';

describe('上海墙钟与真实时刻互转', () => {
  it('往返保持原时刻', () => {
    const iso = '2030-12-20T10:00:00.000Z';
    const picker = toPickerDate(iso);
    expect(picker).not.toBeNull();
    expect(fromPickerDate(picker)?.toISOString()).toBe(iso);
  });

  it('非法与空值返回 null', () => {
    expect(toPickerDate(null)).toBeNull();
    expect(toPickerDate('')).toBeNull();
    expect(toPickerDate('not-a-date')).toBeNull();
    expect(fromPickerDate(null)).toBeNull();
  });

  it('未来上海整点按 +08:00 构造', () => {
    const at = shanghaiDayAt(1, 18);
    expect(at.getTime()).toBeGreaterThan(Date.now());
    const picker = toPickerDate(at.toISOString())!;
    expect(picker.getMinutes()).toBe(0);
    expect(picker.getSeconds()).toBe(0);
    expect(SHANGHAI_OFFSET_MS).toBe(8 * 60 * 60 * 1000);
  });
});
