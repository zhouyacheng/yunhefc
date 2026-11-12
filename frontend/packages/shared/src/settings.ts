/** SET-01/SET-02 的 9 项系统配置（05 §3.12）。 */
export interface SystemSettings {
  maxPlayers: number;
  maxGuests: number;
  maxWaitlist: number;
  groupingManualWeight: number;
  groupingAutoWeight: number;
  groupingAppearanceWeight: number;
  groupingGoalWeight: number;
  groupingAssistWeight: number;
  groupingMinAppearances: number;
}

export const SETTINGS_KEYS = [
  'maxPlayers',
  'maxGuests',
  'maxWaitlist',
  'groupingManualWeight',
  'groupingAutoWeight',
  'groupingAppearanceWeight',
  'groupingGoalWeight',
  'groupingAssistWeight',
  'groupingMinAppearances',
] as const;

export type SettingsKey = (typeof SETTINGS_KEYS)[number];

/** 表单值可能因清空输入而为 null；提交前必须全部为整数。 */
export type SettingsForm = Record<SettingsKey, number | null>;

export type SettingsErrors = Partial<Record<SettingsKey, string>>;

export interface SettingsFieldRule {
  /** 页面展示用标签（与原型一致）。 */
  label: string;
  /** 错误文案中的主语，与后端 40001 的 `requireRange` 标签一致。 */
  messageLabel: string;
  min: number;
  max: number;
}

/** 范围与合计规则与后端 `SettingsApplicationService.validate` 保持一致。 */
export const SETTINGS_FIELD_RULES: Record<SettingsKey, SettingsFieldRule> = {
  maxPlayers: { label: '正式球员报名上限', messageLabel: '报名默认人数', min: 1, max: 99 },
  maxGuests: { label: '外挂球员报名上限', messageLabel: '外挂默认上限', min: 0, max: 99 },
  maxWaitlist: { label: '候补上限', messageLabel: '候补默认上限', min: 0, max: 99 },
  groupingManualWeight: { label: '分组手动权重', messageLabel: '手动权重', min: 0, max: 100 },
  groupingAutoWeight: { label: '分组自动权重', messageLabel: '自动权重', min: 0, max: 100 },
  groupingAppearanceWeight: { label: '出场率权重', messageLabel: '出场权重', min: 0, max: 100 },
  groupingGoalWeight: { label: '进球权重', messageLabel: '进球权重', min: 0, max: 100 },
  groupingAssistWeight: { label: '助攻权重', messageLabel: '助攻权重', min: 0, max: 100 },
  groupingMinAppearances: {
    label: '自动分最少出场',
    messageLabel: '最少出场次数',
    min: 0,
    max: 99,
  },
};

export const SETTINGS_REGISTRATION_KEYS = [
  'maxPlayers',
  'maxGuests',
  'maxWaitlist',
] as const satisfies readonly SettingsKey[];

export const SETTINGS_GROUPING_KEYS = [
  'groupingManualWeight',
  'groupingAutoWeight',
  'groupingAppearanceWeight',
  'groupingGoalWeight',
  'groupingAssistWeight',
  'groupingMinAppearances',
] as const satisfies readonly SettingsKey[];

/** 校验通过后把 9 键表单转成 SET-02 的完整请求体。 */
export function toSettingsPayload(form: SettingsForm): SystemSettings {
  const integer = (value: number | null): number => {
    if (value === null) {
      throw new Error('settings form must be validated before building payload');
    }
    return value;
  };
  return {
    maxPlayers: integer(form.maxPlayers),
    maxGuests: integer(form.maxGuests),
    maxWaitlist: integer(form.maxWaitlist),
    groupingManualWeight: integer(form.groupingManualWeight),
    groupingAutoWeight: integer(form.groupingAutoWeight),
    groupingAppearanceWeight: integer(form.groupingAppearanceWeight),
    groupingGoalWeight: integer(form.groupingGoalWeight),
    groupingAssistWeight: integer(form.groupingAssistWeight),
    groupingMinAppearances: integer(form.groupingMinAppearances),
  };
}

const MANUAL_AUTO_SUM_TEXT = '手动与自动权重合计必须为 100';
const AUTO_PARTS_SUM_TEXT = '出场、进球、助攻权重合计必须大于 0';

function isPresent(value: number | null | undefined): value is number {
  return value !== null && value !== undefined;
}

/**
 * 与 SET-02 的 40001 校验同口径：字段范围逐项校验，再校验两组权重合计。
 * 合计错误同时挂到参与字段上，便于逐项标红。
 */
export function settingsErrors(input: Partial<Record<SettingsKey, number | null>>): SettingsErrors {
  const errors: SettingsErrors = {};
  for (const key of SETTINGS_KEYS) {
    const value = input[key];
    const rule = SETTINGS_FIELD_RULES[key];
    if (!isPresent(value)) {
      errors[key] = '必须填写整数';
    } else if (!Number.isInteger(value)) {
      errors[key] = '必须为整数';
    } else if (value < rule.min || value > rule.max) {
      errors[key] = `${rule.messageLabel}必须在 ${rule.min}-${rule.max} 之间`;
    }
  }

  const manual = input.groupingManualWeight;
  const auto = input.groupingAutoWeight;
  if (
    !errors.groupingManualWeight &&
    !errors.groupingAutoWeight &&
    isPresent(manual) &&
    isPresent(auto) &&
    manual + auto !== 100
  ) {
    errors.groupingManualWeight = MANUAL_AUTO_SUM_TEXT;
    errors.groupingAutoWeight = MANUAL_AUTO_SUM_TEXT;
  }

  const appearance = input.groupingAppearanceWeight;
  const goal = input.groupingGoalWeight;
  const assist = input.groupingAssistWeight;
  if (
    !errors.groupingAppearanceWeight &&
    !errors.groupingGoalWeight &&
    !errors.groupingAssistWeight &&
    isPresent(appearance) &&
    isPresent(goal) &&
    isPresent(assist) &&
    appearance + goal + assist <= 0
  ) {
    errors.groupingAppearanceWeight = AUTO_PARTS_SUM_TEXT;
    errors.groupingGoalWeight = AUTO_PARTS_SUM_TEXT;
    errors.groupingAssistWeight = AUTO_PARTS_SUM_TEXT;
  }

  return errors;
}
