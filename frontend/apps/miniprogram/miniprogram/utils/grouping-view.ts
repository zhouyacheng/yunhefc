import type { GroupedPlayer, GroupingView, MatchSummary } from '../types/api';
import { positionText } from './presentation';
import { starParts, type SkillStarMark } from './skill';

export interface GroupingPlayerRow {
  playerId: number;
  label: string;
  scoreText: string;
  stars: SkillStarMark[];
}

export interface GroupingBucketView {
  key: number;
  label: string;
  players: GroupingPlayerRow[];
}

export interface GroupingColumnView {
  key: 'A' | 'B';
  teamName: string;
  label: string;
  totalText: string;
  buckets: GroupingBucketView[];
}

export interface GroupingDistributionRow {
  key: number;
  label: string;
  a: number;
  b: number;
}

export interface GroupingBoardView {
  hasGrouping: boolean;
  statusText: string;
  diffText: string;
  warnings: string[];
  columns: GroupingColumnView[];
  distribution: GroupingDistributionRow[];
}

const BUCKETS = [
  { key: 0, label: positionText(0) },
  { key: 1, label: positionText(1) },
  { key: 2, label: positionText(2) },
  { key: 3, label: positionText(3) },
  { key: -1, label: '位置未知' },
];

function inBucket(position: number, bucket: number): boolean {
  return bucket === -1 ? position < 0 || position > 3 : position === bucket;
}

function playerLabel(player: GroupedPlayer): string {
  const jersey = player.jersey === null || player.jersey === undefined ? '' : ` #${player.jersey}`;
  return `${player.name}${jersey} · ${positionText(player.position)}`;
}

function totalScore(players: GroupedPlayer[]): string {
  return players.reduce((sum, player) => sum + (player.finalScore ?? 0), 0).toFixed(2);
}

function column(key: 'A' | 'B', label: string, players: GroupedPlayer[]): GroupingColumnView {
  return {
    key,
    teamName: label,
    label: `${key} 队 · ${label}`,
    totalText: totalScore(players),
    buckets: BUCKETS.map((bucket) => ({
      key: bucket.key,
      label: bucket.label,
      players: players
        .filter((player) => inBucket(player.position, bucket.key))
        .map((player) => ({
          playerId: player.playerId,
          label: playerLabel(player),
          scoreText: (player.finalScore ?? 0).toFixed(2),
          stars: starParts(player.finalScore),
        })),
    })),
  };
}

export function buildGroupingBoard(
  grouping: GroupingView,
  match: Pick<MatchSummary, 'homeTeamName' | 'awayTeamName'>,
): GroupingBoardView {
  const hasGrouping = grouping.teams.A.length + grouping.teams.B.length > 0;
  return {
    hasGrouping,
    statusText: hasGrouping ? (grouping.savedAt ? '已保存' : '草稿') : '尚未生成',
    diffText: (grouping.diff ?? 0).toFixed(2),
    warnings: grouping.warnings ?? [],
    columns: [
      column('A', match.homeTeamName, grouping.teams.A),
      column('B', match.awayTeamName, grouping.teams.B),
    ],
    distribution: BUCKETS.map((bucket) => ({
      key: bucket.key,
      label: bucket.label,
      a: grouping.teams.A.filter((player) => inBucket(player.position, bucket.key)).length,
      b: grouping.teams.B.filter((player) => inBucket(player.position, bucket.key)).length,
    })),
  };
}
