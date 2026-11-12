/** 04 §2.4 notification.type；2–6 为预留编码，MVP 不触发。 */
export type NotificationType = 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11;

/** 05 §4 Notification。 */
export interface Notification {
  id: number;
  type: NotificationType;
  typeLabel: string;
  title: string;
  body: string;
  refType?: string | null;
  refId?: number | null;
  readAt?: string | null;
  createdAt: string;
}

/** NTF-02 未读角标。 */
export interface NotificationUnreadCount {
  count: number;
}

/** NTF-03 单条已读；重复已读返回原 readAt。 */
export interface NotificationReadResult {
  readAt: string;
}

/** NTF-04 全部已读；无未读时 updated=0。 */
export interface NotificationReadAllResult {
  updated: number;
}

export type NotificationTarget = { kind: 'match'; id: number } | { kind: 'none' };

/**
 * 通知点击落点：MATCH 且有有效 refId 进入比赛详情；
 * USER、未知 refType 或缺 refId 只标记已读（02 §5.6）。
 */
export function notificationTarget(
  notification: Pick<Notification, 'refType' | 'refId'>,
): NotificationTarget {
  const { refType, refId } = notification;
  if (refType === 'MATCH' && typeof refId === 'number' && Number.isInteger(refId) && refId > 0) {
    return { kind: 'match', id: refId };
  }
  return { kind: 'none' };
}
