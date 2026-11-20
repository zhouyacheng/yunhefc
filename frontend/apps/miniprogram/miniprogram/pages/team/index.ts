import type { PlayerPublic } from '../../types/api';
import { request } from '../../utils/request';

interface Group {
  position: number;
  label: string;
  players: PlayerPublic[];
}

interface PickerEvent {
  detail: { value: string };
}

const GROUP_LABELS: Record<number, string> = {
  0: '门将',
  1: '后卫',
  2: '中场',
  3: '前锋',
};

function groupByPosition(players: PlayerPublic[]): Group[] {
  const groups: Group[] = [0, 1, 2, 3].map((position) => ({
    position,
    label: GROUP_LABELS[position],
    players: [],
  }));
  for (const player of players) {
    const group = groups.find((item) => item.position === player.position);
    if (group) {
      group.players.push(player);
    }
  }
  return groups.filter((group) => group.players.length > 0);
}

Page({
  data: {
    seasons: [] as string[],
    seasonIndex: 0,
    groups: [] as Group[],
    loading: true,
    error: '',
  },

  onShow() {
    void this.load();
  },

  onSeasonChange(event: PickerEvent) {
    this.setData({ seasonIndex: Number(event.detail.value) });
    void this.loadPlayers();
  },

  async load() {
    this.setData({ loading: true, error: '' });
    try {
      const result = await request<{ items: string[] }>('GET', '/matches/seasons');
      const seasons = (result.items || []).slice().sort((a, b) => b.localeCompare(a));
      const current = String(new Date().getFullYear());
      if (!seasons.includes(current)) {
        seasons.unshift(current);
      }
      this.setData({ seasons, seasonIndex: Math.min(this.data.seasonIndex, seasons.length - 1) });
    } catch {
      // 赛季选项失败不阻塞球员列表，退化为不传 season。
    }
    await this.loadPlayers();
  },

  async loadPlayers() {
    const season = this.data.seasons[this.data.seasonIndex];
    const query = season ? `?all=true&season=${encodeURIComponent(season)}` : '?all=true';
    this.setData({ loading: true, error: '' });
    try {
      const result = await request<{ items: PlayerPublic[] }>('GET', `/team/players${query}`);
      this.setData({ groups: groupByPosition(result.items || []) });
    } catch (error) {
      this.setData({ groups: [], error: (error as Error).message || '加载失败，请稍后重试' });
    } finally {
      this.setData({ loading: false });
    }
  },
});
