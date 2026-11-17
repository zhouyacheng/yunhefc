import type { ApiClient } from '@yunhefc/api-client';
import type { MatchStatus, MatchSummary } from '@yunhefc/shared';

/** 空串 season = 全部赛季；空串 status = 全部状态。 */
export interface MatchFilters {
  season: string;
  /** YYYY-MM 成对；null = 全部月份。 */
  monthRange: [string, string] | null;
  status: MatchStatus | '';
}

/** catalog 看到的唯一分页结果，不区分服务端分页与快照分页。 */
export interface MatchCatalogPage {
  items: MatchSummary[];
  total: number;
  page: number;
  pageSize: number;
  totalPages: number;
  /** 仅 internalOnly 快照可能为 true。 */
  truncated: boolean;
}

export interface MatchListOptions {
  /** internalOnly 快照 source 用它绕过缓存。 */
  force?: boolean;
}

export interface MatchCatalogSource {
  list(filters: MatchFilters, page: number, options?: MatchListOptions): Promise<MatchCatalogPage>;
  byId(id: number): Promise<MatchSummary | null>;
  /** 失败返回 []，不向上抛；赛季选项不是阻塞数据。 */
  seasons(): Promise<string[]>;
}

/** 只依赖 get，便于用 fake 替换 ApiClient。 */
export type MatchApiClient = Pick<ApiClient, 'get'>;
