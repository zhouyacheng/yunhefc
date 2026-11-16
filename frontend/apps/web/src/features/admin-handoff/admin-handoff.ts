import type { AdminTicket } from '@yunhefc/shared';

import { api } from '../../api/client';

export interface AdminTarget {
  path?: string;
  query?: Record<string, string | number | boolean | null | undefined>;
}

interface OpenAdminDeps {
  adminBase?: string;
  issueTicket?: () => Promise<AdminTicket>;
  navigate?: (url: string) => void;
}

function defaultAdminBase(): string {
  return (
    import.meta.env.VITE_ADMIN_URL ??
    (import.meta.env.DEV ? 'http://localhost:5274/admin/' : '/admin/')
  );
}

/** 纯回退链接；中键/新标签页打开时不带票据，由 B 端登录。 */
export function adminUrl(target: AdminTarget = {}, base = defaultAdminBase()): string {
  const normalizedBase = base.endsWith('/') ? base : `${base}/`;
  const path = (target.path ?? '').replace(/^\/+/, '');
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(target.query ?? {})) {
    if (value === null || value === undefined || value === '') {
      continue;
    }
    params.set(key, String(value));
  }
  const serialized = params.toString();
  return `${normalizedBase}${path}${serialized ? `?${serialized}` : ''}`;
}

/**
 * 从 C 端进入 B 端：先签发一次性票据，再整页跳转。
 * 票据放 fragment，不进入服务端日志；签发失败时回退纯链接，由 B 端登录页兜底。
 */
export async function openAdmin(target: AdminTarget = {}, deps: OpenAdminDeps = {}): Promise<void> {
  const base = deps.adminBase ?? defaultAdminBase();
  const plainUrl = adminUrl(target, base);
  const navigate = deps.navigate ?? ((url: string) => window.location.assign(url));
  const issueTicket =
    deps.issueTicket ??
    (() =>
      api.post<AdminTicket>('/auth/admin-ticket', undefined, { redirectOnUnauthorized: false }));

  try {
    const { ticket } = await issueTicket();
    navigate(`${plainUrl}#ticket=${encodeURIComponent(ticket)}`);
  } catch {
    navigate(plainUrl);
  }
}
