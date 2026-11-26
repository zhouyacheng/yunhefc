import type { LedgerCategory, LedgerType } from '@yunhefc/shared';
import { LEDGER_TYPE_TEXT, ledgerDirectionText, ledgerTypeText } from '@yunhefc/shared';

export { LEDGER_TYPE_TEXT, ledgerDirectionText, ledgerTypeText };

/** 05 §3.17：金额 >0 且 <=99,999,999.99，最多两位小数。 */
export const LEDGER_AMOUNT_MAX = 99_999_999.99;

export const LEDGER_TYPE_OPTIONS = Object.entries(LEDGER_TYPE_TEXT).map(([value, text]) => ({
  value: Number(value) as LedgerType,
  text,
}));

/** 分类方向：收入/支出/通用（null）。 */
export const LEDGER_DIRECTION_OPTIONS = [{ value: '', text: '通用' }, ...LEDGER_TYPE_OPTIONS];

/** 金额输入校验；返回错误文案或 null。 */
export function validateLedgerAmount(value: string): string | null {
  const text = value.trim();
  if (!text) {
    return '请输入金额';
  }
  if (!/^\d+(\.\d{1,2})?$/.test(text)) {
    return Number(text) <= 0 ? '金额必须大于 0' : '金额最多两位小数';
  }
  const amount = Number(text);
  if (!Number.isFinite(amount) || amount <= 0) {
    return '金额必须大于 0';
  }
  if (amount > LEDGER_AMOUNT_MAX) {
    return '金额不能超过 99,999,999.99';
  }
  return null;
}

/** 摘要可选；空白视为无摘要，超 255 字仍拒绝。 */
export function validateLedgerDescription(value: string): string | null {
  return value.trim().length > 255 ? '摘要不能超过 255 字' : null;
}

/** 新建/编辑下拉：仅启用分类，且方向匹配或为通用分类。 */
export function categoriesForType(
  categories: readonly LedgerCategory[],
  type: LedgerType,
): LedgerCategory[] {
  return categories.filter(
    (category) => category.isActive && (category.direction === null || category.direction === type),
  );
}
