// 排名单源宿主在小程序（DevTools 只编译 miniprogramRoot 内文件），Web 反向引用；
// 本文件仅保留适配层，避免 Web 侧直接依赖小程序包路径分散在各处。
export {
  DEFAULT_LEADERBOARD_BOARD,
  LEADERBOARD_BOARD_KEYS,
  LEADERBOARD_BOARDS,
  leaderboardBoard,
  rankEntries,
} from '@yunhefc/miniprogram/utils/leaderboard-engine';
export type {
  LeaderboardBoardKey,
  RankedEntry,
} from '@yunhefc/miniprogram/utils/leaderboard-engine';
