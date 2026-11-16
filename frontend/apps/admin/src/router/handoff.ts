import type { LocationQuery, LocationQueryRaw } from 'vue-router';

/** 从 URL fragment 读取 C 端签发的一次性票据；非法值按无票据处理。 */
export function readTicket(hash: string): string | undefined {
  const raw = hash.replace(/^#/, '');
  if (!raw) {
    return undefined;
  }
  const ticket = new URLSearchParams(raw).get('ticket')?.trim();
  if (!ticket || ticket.length > 128) {
    return undefined;
  }
  return ticket;
}

/** 兑换后清除 fragment，保留路径与查询参数。 */
export function stripTicket(location: { path: string; query: LocationQuery; hash: string }): {
  path: string;
  query: LocationQueryRaw;
  hash: string;
} {
  return { path: location.path, query: { ...location.query }, hash: '' };
}
