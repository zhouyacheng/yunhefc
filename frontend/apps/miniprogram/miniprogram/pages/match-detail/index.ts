import type { MatchDetail, MatchDetailView, MatchEvent } from '../../types/api';
import { request } from '../../utils/request';

interface EventTimeline {
  items: MatchEvent[];
  derivedScore: { scoreHome: number; scoreAway: number } | null;
}

interface EventRow {
  id: number;
  label: string;
  minuteText: string;
  playerName: string;
}

function pad(value: number): string {
  return String(value).padStart(2, '0');
}

Page({
  data: {
    id: 0,
    detail: null as MatchDetailView | null,
    events: [] as EventRow[],
    loading: true,
    error: '',
  },

  onLoad(query: Record<string, string | undefined>) {
    const id = Number(query.id || 0);
    this.setData({ id });
    void this.load();
  },

  async load() {
    const id = this.data.id;
    if (!id) {
      this.setData({ loading: false, error: '比赛不存在' });
      return;
    }
    this.setData({ loading: true, error: '' });
    try {
      const [detail, timeline] = await Promise.all([
        request<MatchDetail>('GET', `/matches/${id}`),
        request<EventTimeline>('GET', `/matches/${id}/events`),
      ]);
      const start = new Date(detail.startAt);
      const end = new Date(detail.endAt);
      const timeText = Number.isNaN(start.getTime())
        ? detail.startAt
        : `${start.getFullYear()}-${pad(start.getMonth() + 1)}-${pad(start.getDate())} ` +
          `${pad(start.getHours())}:${pad(start.getMinutes())}` +
          (Number.isNaN(end.getTime()) ? '' : `-${pad(end.getHours())}:${pad(end.getMinutes())}`);
      this.setData({
        detail: { ...detail, timeText } satisfies MatchDetailView,
        events: (timeline.items || []).map((event) => ({
          id: event.id,
          label: event.typeLabel,
          minuteText: `${event.minute}'`,
          playerName: event.playerName || '',
        })),
      });
    } catch (error) {
      this.setData({ error: (error as Error).message || '加载失败，请稍后重试' });
    } finally {
      this.setData({ loading: false });
    }
  },
});
