// 与 Web SkillStars 一致：两位小数按半星粒度拆成 5 格。
export type SkillStarPart = 'full' | 'half' | 'empty';

export interface SkillStarMark {
  part: SkillStarPart;
  key: number;
}

export function starParts(skill: number | null | undefined): SkillStarMark[] {
  const value = Number(skill ?? 0);
  const hundredths = Number.isFinite(value)
    ? Math.max(0, Math.min(500, Math.round(value * 100)))
    : 0;
  const full = Math.floor(hundredths / 100);
  const half = hundredths % 100 >= 50;
  const empty = 5 - full - (half ? 1 : 0);
  return [
    ...Array.from({ length: full }, () => 'full' as const),
    ...(half ? (['half'] as const) : []),
    ...Array.from({ length: empty }, () => 'empty' as const),
  ].map((part, key) => ({ part, key }));
}
