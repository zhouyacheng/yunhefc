import { describe, expect, it } from 'vitest';

import type { GroupingView, MatchSummary } from '../miniprogram/types/api';
import { buildGroupingBoard } from '../miniprogram/utils/grouping-view';

function match(): Pick<MatchSummary, 'homeTeamName' | 'awayTeamName'> {
  return { homeTeamName: '橙队', awayTeamName: '兰队' };
}

const grouping: GroupingView = {
  savedAt: '2026-12-06T12:00:00+08:00',
  diff: 0.5,
  warnings: ['门将人数不均衡'],
  teams: {
    A: [
      { playerId: 1, name: '甲', jersey: 1, position: 0, finalScore: 6 },
      { playerId: 2, name: '乙', jersey: 2, position: 2, finalScore: 5 },
    ],
    B: [{ playerId: 3, name: '丙', jersey: 3, position: 2, finalScore: 5.5 }],
  },
};

describe('buildGroupingBoard', () => {
  it('builds saved A/B columns with labels, scores and distribution', () => {
    const board = buildGroupingBoard(grouping, match());

    expect(board.hasGrouping).toBe(true);
    expect(board.statusText).toBe('已保存');
    expect(board.diffText).toBe('0.50');
    expect(board.warnings).toEqual(['门将人数不均衡']);
    expect(board.columns.map((column) => column.label)).toEqual(['A 队 · 橙队', 'B 队 · 兰队']);
    expect(board.columns.map((column) => column.teamName)).toEqual(['橙队', '兰队']);
    expect(board.columns[0].totalText).toBe('11.00');
    expect(board.columns[0].buckets[0].players[0]).toMatchObject({
      label: '甲 #1 · 门将',
      scoreText: '6.00',
    });
    expect(board.columns[0].buckets[0].players[0].stars).toHaveLength(5);
    expect(board.columns[1].buckets[2].players[0].stars.map((star) => star.part)).toEqual([
      'full',
      'full',
      'full',
      'full',
      'full',
    ]);
    expect(board.distribution[2]).toMatchObject({ label: '中场', a: 1, b: 1 });
  });

  it('shows a draft when savedAt is missing and handles empty grouping', () => {
    expect(buildGroupingBoard({ ...grouping, savedAt: null }, match()).statusText).toBe('草稿');
    expect(
      buildGroupingBoard({ ...grouping, teams: { A: [], B: [] }, savedAt: null }, match()),
    ).toMatchObject({ hasGrouping: false, statusText: '尚未生成' });
  });
});
