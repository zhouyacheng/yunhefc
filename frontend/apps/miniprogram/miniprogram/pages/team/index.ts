import { fetchTeamStats, listMatchSeasons, listTeamPlayers } from '../../utils/api-client';
import { currentSeason, mergeSeasonOptions } from '../../utils/season';
import { buildTeamGroups, type TeamGroupView } from '../../utils/team-view';

interface PickerEvent {
  detail: { value: string };
}

let requestSeq = 0;

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

  openLeaderboard() {
    const season = this.data.seasons[this.data.seasonIndex] || currentSeason();
    void wx.navigateTo({
      url: `/pages/leaderboard/index?season=${encodeURIComponent(season)}`,
    });
  },

  async load() {
    await this.loadSeasonOptions();
    await this.loadPlayers();
  },

  async loadSeasonOptions() {
    const selected = this.data.seasons[this.data.seasonIndex] || currentSeason();
    let items: string[] = [];
    try {
      items = await listMatchSeasons();
    } catch {
      // 选项失败不阻塞名单与统计；至少保留当前年份与已选赛季。
    }
    const seasons = mergeSeasonOptions(items, selected);
    this.setData({ seasons, seasonIndex: Math.max(0, seasons.indexOf(selected)) });
  },

  async loadPlayers() {
    const seq = ++requestSeq;
    const season = this.data.seasons[this.data.seasonIndex] || undefined;
    this.setData({ loading: true, error: '' });

    // 统计允许延迟：先渲染 PLR-01 名单，统计返回后再合并；缺失时降级为「—」。
    const statsRequest = fetchTeamStats(season).catch(() => null);

    try {
      const players = await listTeamPlayers({ all: true, season });
      if (seq !== requestSeq) {
        return;
      }
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
