/**
 * 04 §2.4 notification.type；2–6 为预留编码，MVP 不触发；12/13 为活动创建/取消；
 * 14 为内容举报（管理员接收）。
 */
export type NotificationType = 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12 | 13 | 14;

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

export type NotificationTarget =
  | { kind: 'match'; id: number }
  | { kind: 'post'; id: number }
  | { kind: 'album'; id: number }
  | { kind: 'none' };

/**
 * 通知点击落点：MATCH 且有有效 refId 进入比赛详情；
 * CONTENT_POST 进入时间线定位动态，CONTENT_PHOTO 进入对应相册；
 * USER、未知 refType 或缺 refId 只标记已读（02 §5.6、§5.15）。
 */
export function notificationTarget(
  notification: Pick<Notification, 'refType' | 'refId'>,
): NotificationTarget {
  const { refType, refId } = notification;
  if (typeof refId !== 'number' || !Number.isInteger(refId) || refId <= 0) {
    return { kind: 'none' };
  }
  if (refType === 'MATCH') {
    return { kind: 'match', id: refId };
  }
  if (refType === 'CONTENT_POST') {
    return { kind: 'post', id: refId };
  }
  if (refType === 'CONTENT_PHOTO') {
    return { kind: 'album', id: refId };
  }
  return { kind: 'none' };
}
