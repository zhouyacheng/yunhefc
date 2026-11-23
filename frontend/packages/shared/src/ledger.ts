/** M16 球队账本（05 §3.17、§4）。 */

/** 0-收入；1-支出。 */
export type LedgerType = 0 | 1;

/** 分类方向：0-收入；1-支出；null-通用。 */
export type LedgerDirection = LedgerType | null;

export interface LedgerCategory {
  id: number;
  name: string;
  direction: LedgerDirection;
  directionLabel: string | null;
  sortNo: number;
  isActive: boolean;
  entryCount: number;
}

/** LED-01/03/04/05/06。 */
export interface LedgerEntry {
  id: number;
  type: LedgerType;
  typeLabel: string;
  categoryId: number;
  categoryName: string;
  categoryDirection: LedgerDirection;
  amount: number;
  description: string;
  occurredAt: string;
  playerId: number | null;
  playerName: string | null;
  activityId: number | null;
  activityTitle: string | null;
  version: number;
}

/** LED-02/07：余额 = 收入 − 支出，无期初余额。 */
export interface LedgerSummary {
  income: number;
  expense: number;
  balance: number;
}
