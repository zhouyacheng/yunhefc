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
