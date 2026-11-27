// 比赛/活动分享封面色板：比赛沿用冷色绿强调，活动使用深绿基底 + 暖琥珀/金色强调。
export interface ShareCoverPalette {
  background: { from: string; mid: string; to: string };
  glow: { center: string; edge: string };
  rings: { strong: string; soft: string };
  /** 竖向分隔线描边。 */
  divider: string;
  pill: { fill: string; border: string; text: string };
  brand: string;
  title: string;
  meta: { date: string; text: string; quota: string; extra: string };
  footer: { dot: string; text: string };
}

export const MATCH_COVER_PALETTE: ShareCoverPalette = {
  background: { from: '#0b1220', mid: '#111c33', to: '#0f2a24' },
  glow: { center: 'rgba(22, 163, 74, 0.45)', edge: 'rgba(22, 163, 74, 0)' },
  rings: {
    strong: 'rgba(255, 255, 255, 0.12)',
    soft: 'rgba(255, 255, 255, 0.08)',
  },
  divider: 'rgba(255, 255, 255, 0.08)',
  pill: {
    fill: 'rgba(22, 163, 74, 0.18)',
    border: 'rgba(134, 239, 172, 0.45)',
    text: '#dcfce7',
  },
  brand: '#f5c451',
  title: '#ffffff',
  meta: {
    date: '#e2e8f0',
    text: '#cbd5e1',
    quota: '#94a3b8',
    extra: '#64748b',
  },
  footer: { dot: '#16a34a', text: '#94a3b8' },
};

export const ACTIVITY_COVER_PALETTE: ShareCoverPalette = {
  background: { from: '#0c1712', mid: '#173327', to: '#4a2f14' },
  glow: {
    center: 'rgba(245, 196, 81, 0.48)',
    edge: 'rgba(245, 196, 81, 0)',
  },
  rings: {
    strong: 'rgba(255, 245, 230, 0.10)',
    soft: 'rgba(255, 245, 230, 0.07)',
  },
  divider: 'rgba(255, 245, 230, 0.07)',
  pill: {
    fill: 'rgba(245, 196, 81, 0.16)',
    border: 'rgba(253, 230, 138, 0.45)',
    text: '#fde68a',
  },
  brand: '#f5c451',
  title: '#fbbf24',
  meta: {
    date: '#f3ead7',
    text: '#e8dcc4',
    quota: '#cdbfa5',
    extra: '#a89b84',
  },
  footer: { dot: '#f5c451', text: '#cdbfa5' },
};
