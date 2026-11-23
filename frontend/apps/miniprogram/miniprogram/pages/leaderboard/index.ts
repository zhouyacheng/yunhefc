import type { LeaderboardEntry } from '../../types/api';
import { fetchLeaderboards, listMatchSeasons } from '../../utils/api-client';
import {
  buildLeaderboardRows,
  LEADERBOARD_BOARD_TABS,
  type LeaderboardBoardKey,
  type LeaderboardRowView,
} from '../../utils/leaderboard-view';
import { currentSeason, mergeSeasonOptions } from '../../utils/season';

interface PickerEvent {
  detail: { value: string };
}

interface WindowOption {
  key: string;
  label: string;
}

const ALL_WINDOW: WindowOption = { key: 'all', label: '生涯累计' };

function windowLabel(key: string): string {
  return key === 'all' ? ALL_WINDOW.label : `${key} 赛季`;
}

Page({
  data: {
    windows: [ALL_WINDOW] as WindowOption[],
    windowIndex: 0,
    windowKey: '',
    boards: LEADERBOARD_BOARD_TABS,
    boardKey: 'appearances' as LeaderboardBoardKey,
    rows: [] as LeaderboardRowView[],
    summary: '',
    loading: true,
    error: '',
  },

  initialSeason: '',
  requestSeq: 0,
  entries: [] as LeaderboardEntry[],
  failedAvatars: [] as number[],

  onLoad(query: Record<string, string | undefined>) {
    const raw = query?.season;
    this.initialSeason = typeof raw === 'string' && /^\d{4}$/.test(raw) ? raw : currentSeason();
  },

  onShow() {
    void this.load();
  },

  async load() {
    await this.loadWindows();
    await this.loadBoards();
  },

  /** MAT-14 赛季选项 + 生涯累计；失败不阻塞榜单，至少保留当前与已选赛季。 */
  async loadWindows() {
    const fallback = this.initialSeason || currentSeason();
    const selected = this.data.windowKey || fallback;
    let items: string[] = [];
    try {
      items = await listMatchSeasons();
    } catch {
      // 选项失败只影响下拉范围。
    }
    const seasons = mergeSeasonOptions(items, selected === 'all' ? fallback : selected);
    const windows: WindowOption[] = [
      ALL_WINDOW,
      ...seasons.map((season) => ({ key: season, label: season })),
    ];
    const index = Math.max(
      0,
      windows.findIndex((window) => window.key === selected),
    );
    this.setData({ windows, windowIndex: index, windowKey: windows[index].key });
  },

  /** 每个窗口只请求一次 STA-03；榜单切换仅在前端重排。 */
  async loadBoards() {
    const seq = ++this.requestSeq;
    const windowKey = this.data.windowKey;
    this.failedAvatars = [];
    this.setData({ loading: true, error: '' });

    try {
      const data = await fetchLeaderboards({
        season: windowKey,
        scope: windowKey === 'all' ? 'all' : 'season',
      });
      if (seq !== this.requestSeq) {
        return;
      }
      this.entries = Array.isArray(data.items) ? data.items : [];
      this.setData({
        rows: this.renderRows(this.data.boardKey),
        summary: `${this.entries.length} 名正式球员 · ${windowLabel(windowKey)} · 最多延迟 1 分钟`,
        loading: false,
      });
    } catch (error) {
      if (seq !== this.requestSeq) {
        return;
      }
      this.entries = [];
      this.setData({
        rows: [],
        summary: '',
        loading: false,
        error: (error as Error).message || '加载失败，请稍后重试',
      });
    }
  },

  /** 重排当前窗口缓存；头像加载失败的球员继续回落姓名首字。 */
  renderRows(key: LeaderboardBoardKey): LeaderboardRowView[] {
    return buildLeaderboardRows(this.entries, key).map((row) =>
      this.failedAvatars.includes(row.playerId) ? { ...row, avatarUrl: '' } : row,
    );
  },

  onWindowChange(event: PickerEvent) {
    const windowIndex = Number(event.detail.value);
    if (windowIndex === this.data.windowIndex) {
      return;
    }
    const window = this.data.windows[windowIndex];
    if (!window) {
      return;
    }
    this.setData({ windowIndex, windowKey: window.key });
    void this.loadBoards();
  },

  onBoardChange(event: WechatMiniprogram.BaseEvent) {
    const key = event.currentTarget.dataset.key as LeaderboardBoardKey;
    if (!key || key === this.data.boardKey) {
      return;
    }
    this.setData({ boardKey: key, rows: this.renderRows(key) });
  },

  retry() {
    void this.load();
  },

  onAvatarError(event: WechatMiniprogram.BaseEvent) {
    const playerId = Number(event.currentTarget.dataset.id);
    if (!playerId || this.failedAvatars.includes(playerId)) {
      return;
    }
    this.failedAvatars = [...this.failedAvatars, playerId];
    this.setData({
      rows: this.data.rows.map((row) =>
        row.playerId === playerId ? { ...row, avatarUrl: '' } : row,
      ),
    });
  },
});
