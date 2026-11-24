import { describe, expect, it } from 'vitest';

import {
  ACTIVITY_IMAGE_MAX_BYTES,
  activityEditable,
  activityFormError,
  activityStatusText,
  activityTypeText,
  validateActivityImage,
} from './form';

const base = {
  title: '冬季聚餐',
  startAt: new Date('2030-12-20T18:00:00+08:00'),
  endAt: new Date('2030-12-20T20:00:00+08:00'),
  signupDeadline: new Date('2030-12-20T17:00:00+08:00'),
  capacity: 20,
  familyAllowed: false,
  costPerPerson: '88.50',
  location: '队内餐厅',
  locationImageKeys: [],
  description: '',
};

describe('activityFormError', () => {
  it('接受合法表单', () => {
    expect(activityFormError(base)).toBeNull();
    expect(
      activityFormError({ ...base, endAt: null, capacity: null, costPerPerson: '' }),
    ).toBeNull();
  });

  it('校验名称、地址与说明', () => {
    expect(activityFormError({ ...base, title: '  ' })).toBe('请输入活动名称');
    expect(activityFormError({ ...base, location: '' })).toBe('请输入活动地址');
    expect(activityFormError({ ...base, description: 'x'.repeat(1001) })).toContain('1000');
  });

  it('校验时间关系', () => {
    expect(activityFormError({ ...base, startAt: null })).toBe('请选择活动时间');
    expect(activityFormError({ ...base, signupDeadline: null })).toBe('请选择报名截止');
    expect(
      activityFormError({
        ...base,
        signupDeadline: new Date(base.startAt.getTime() + 1),
      }),
    ).toBe('报名截止不能晚于活动时间');
    expect(activityFormError({ ...base, endAt: new Date(base.startAt.getTime()) })).toBe(
      '结束时间必须晚于活动时间',
    );
  });

  it('校验人数与人均消费', () => {
    expect(activityFormError({ ...base, capacity: 0 })).toContain('1–65535');
    expect(activityFormError({ ...base, capacity: 1.5 })).toContain('1–65535');
    expect(activityFormError({ ...base, costPerPerson: '-1' })).toBe(
      '人均消费必须为非负数，最多两位小数',
    );
    expect(activityFormError({ ...base, costPerPerson: '88.555' })).toBe(
      '人均消费必须为非负数，最多两位小数',
    );
    expect(activityFormError({ ...base, costPerPerson: '0' })).toBeNull();
  });

  it('场地环境图片最多 10 张', () => {
    expect(activityFormError({ ...base, locationImageKeys: Array(10).fill('key') })).toBeNull();
    expect(activityFormError({ ...base, locationImageKeys: Array(11).fill('key') })).toBe(
      '场地环境图片最多 10 张',
    );
  });
});

describe('validateActivityImage', () => {
  it('允许 JPG/PNG/WebP 且不超过 5 MiB', () => {
    expect(validateActivityImage({ type: 'image/png', size: ACTIVITY_IMAGE_MAX_BYTES })).toBeNull();
    expect(validateActivityImage({ type: 'image/gif', size: 10 })).toBe('仅支持 JPG/PNG/WebP');
    expect(validateActivityImage({ type: 'image/jpeg', size: ACTIVITY_IMAGE_MAX_BYTES + 1 })).toBe(
      '图片不能超过 5MB',
    );
  });
});

describe('活动展示与可编辑状态', () => {
  it('映射中文文案', () => {
    expect(activityTypeText(0)).toBe('聚餐');
    expect(activityTypeText(2)).toBe('其他');
    expect(activityStatusText(0)).toBe('报名中');
    expect(activityStatusText(3)).toBe('已取消');
  });

  it('仅报名中与报名截止可编辑', () => {
    expect(activityEditable(0)).toBe(true);
    expect(activityEditable(1)).toBe(true);
    expect(activityEditable(2)).toBe(false);
    expect(activityEditable(3)).toBe(false);
  });
});
