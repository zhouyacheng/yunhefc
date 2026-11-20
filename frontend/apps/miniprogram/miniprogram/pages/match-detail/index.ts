import type { MatchDetail, MatchDetailView, MatchEvent } from '../../types/api';
import { formatDateYmd, formatTimeRange } from '../../utils/datetime';
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
      const date = formatDateYmd(detail.startAt);
      const timeText = date
        ? `${date} ${formatTimeRange(detail.startAt, detail.endAt)}`
        : detail.startAt;
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
