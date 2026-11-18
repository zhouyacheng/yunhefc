/** SET-01/SET-02 的 3 项系统配置（05 §3.12）。 */
export interface SystemSettings {
  maxPlayers: number;
  maxGuests: number;
  maxWaitlist: number;
}

export const SETTINGS_KEYS = ['maxPlayers', 'maxGuests', 'maxWaitlist'] as const;

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

/** 范围规则与后端 `SettingsApplicationService.validate` 保持一致。 */
export const SETTINGS_FIELD_RULES: Record<SettingsKey, SettingsFieldRule> = {
  maxPlayers: { label: '正式球员报名上限', messageLabel: '报名默认人数', min: 1, max: 99 },
  maxGuests: { label: '外挂球员报名上限', messageLabel: '外挂默认上限', min: 0, max: 99 },
  maxWaitlist: { label: '候补上限', messageLabel: '候补默认上限', min: 0, max: 99 },
};

export const SETTINGS_REGISTRATION_KEYS = [
  'maxPlayers',
  'maxGuests',
  'maxWaitlist',
] as const satisfies readonly SettingsKey[];

/** 校验通过后把 3 键表单转成 SET-02 的完整请求体。 */
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
  };
}

function isPresent(value: number | null | undefined): value is number {
  return value !== null && value !== undefined;
}

/** 与 SET-02 的 40001 校验同口径：字段范围逐项校验。 */
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
  return errors;
}
