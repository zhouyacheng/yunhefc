import type { PlayerPublic, TeamStats } from '../../types/api';
import { currentShanghaiMonth } from '../../utils/datetime';
import { request } from '../../utils/request';
import { buildTeamGroups, type TeamGroupView } from '../../utils/team-view';

interface PickerEvent {
  detail: { value: string };
}

let requestSeq = 0;

function currentSeason(): string {
  return currentShanghaiMonth().slice(0, 4) || String(new Date().getFullYear());
}

/** 与 Web mergeSeasonOptions 一致：过滤非法值、去重、倒序，并包含当前与已选赛季。 */
function mergeSeasonOptions(items: readonly string[], selected: string): string[] {
  const seasons = new Set<string>([currentSeason(), selected]);
  for (const item of items) {
    if (/^\d{4}$/.test(item)) {
      seasons.add(item);
    }
  }
  return [...seasons].sort((a, b) => b.localeCompare(a));
}

Page({
  data: {
    seasons: [] as string[],
    seasonIndex: 0,
    groups: [] as TeamGroupView[],
    loading: true,
    error: '',
  },

  onShow() {
    void this.load();
  },

  onSeasonChange(event: PickerEvent) {
    const seasonIndex = Number(event.detail.value);
    if (seasonIndex === this.data.seasonIndex) {
      return;
    }
    this.setData({ seasonIndex });
    void this.loadPlayers();
  },

  async load() {
    await this.loadSeasonOptions();
    await this.loadPlayers();
  },

  async loadSeasonOptions() {
    const selected = this.data.seasons[this.data.seasonIndex] || currentSeason();
    let items: string[] = [];
    try {
      const result = await request<{ items: string[] }>('GET', '/matches/seasons');
      items = result.items || [];
    } catch {
      // 选项失败不阻塞名单与统计；至少保留当前年份与已选赛季。
    }
    const seasons = mergeSeasonOptions(items, selected);
    this.setData({ seasons, seasonIndex: Math.max(0, seasons.indexOf(selected)) });
  },

  async loadPlayers() {
    const seq = ++requestSeq;
    const season = this.data.seasons[this.data.seasonIndex];
    const seasonQuery = season ? `season=${encodeURIComponent(season)}` : '';
    const playersQuery = seasonQuery ? `?all=true&${seasonQuery}` : '?all=true';
    const statsQuery = seasonQuery ? `?${seasonQuery}` : '';
    this.setData({ loading: true, error: '' });

    // 统计允许延迟：先渲染 PLR-01 名单，统计返回后再合并；缺失时降级为「—」。
    const statsRequest = request<TeamStats>('GET', `/team/stats${statsQuery}`).catch(() => null);

    try {
      const result = await request<{ items: PlayerPublic[] }>(
        'GET',
        `/team/players${playersQuery}`,
      );
      if (seq !== requestSeq) {
        return;
      }
      const players = result.items || [];
      this.setData({ groups: buildTeamGroups(players, []), loading: false });
      const stats = await statsRequest;
      if (seq !== requestSeq || !stats) {
        return;
      }
      this.setData({ groups: buildTeamGroups(players, stats.items || []) });
    } catch (error) {
      if (seq !== requestSeq) {
        return;
      }
      this.setData({
        groups: [],
        loading: false,
        error: (error as Error).message || '加载失败，请稍后重试',
      });
    }
  },

  onAvatarError(event: WechatMiniprogram.BaseEvent) {
    const id = Number(event.currentTarget.dataset.id);
    if (!id) {
      return;
    }
    this.setData({
      groups: this.data.groups.map((group) => ({
        ...group,
        players: group.players.map((player) =>
          player.id === id ? { ...player, avatarUrl: '' } : player,
        ),
      })),
    });
  },

  retry() {
    void this.loadPlayers();
  },
});
