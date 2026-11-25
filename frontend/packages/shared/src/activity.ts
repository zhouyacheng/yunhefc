/** M15 队内活动（05 §3.16、§4）。 */

/** 0-聚餐；1-年会；2-其他。 */
export type ActivityType = 0 | 1 | 2;

/** 0-报名中；1-报名截止；2-已结束；3-已取消。 */
export type ActivityStatus = 0 | 1 | 2 | 3;

/** 0-已报名；1-已取消。 */
export type ActivitySignupStatus = 0 | 1;

/** ACT-01/02：列表与详情均不含报名名单。 */
export interface ActivitySummary {
  id: number;
  title: string;
  type: ActivityType;
  typeLabel: string;
  startAt: string;
  endAt: string | null;
  signupDeadline: string;
  /** 只计报名人数，不含家属；null 为不限。 */
  capacity: number | null;
  /** 活动级开关；关闭时报名不展示家属选项。 */
  familyAllowed: boolean;
  costPerPerson: number | null;
  location: string;
  /** 按展示顺序现签，0–10 项。 */
  locationImageUrls: string[];
  description: string | null;
  status: ActivityStatus;
  statusLabel: string;
  signupCount: number;
  signupOpen: boolean;
  /** ACT-13 乐观锁版本；详情的状态修改回传。 */
  version: number;
  /** 仅登录时返回；对齐 05 §4 与 MatchDetail.myRegistration 的可选口径。 */
  mySignup?: ActivitySignup | null;
}

export type ActivityDetail = ActivitySummary;

/** ACT-06/07/08/09/10/13：管理端额外返回创建人与原始图片对象键。 */
export interface ActivityAdmin extends ActivitySummary {
  /** 写请求回传的原始对象键；编辑与复制用它回填图片，展示仍用 locationImageUrls。 */
  locationImageKeys: string[];
  createdBy: number;
}

/** ACT-03/04/05/11/12。 */
export interface ActivitySignup {
  id: number;
  activityId: number;
  playerId: number;
  playerName: string;
  playerNo: number | null;
  avatarUrl: string | null;
  withFamily: boolean;
  familyNote: string | null;
  status: ActivitySignupStatus;
  statusLabel: string;
  signedUpAt: string;
}

export type ActivitySignupRow = ActivitySignup;

/** 三端共用的展示文案；服务端 fieldLabel 是英文枚举名，不可直接展示。 */
export const ACTIVITY_TYPE_TEXT: Readonly<Record<number, string>> = {
  0: '聚餐',
  1: '年会',
  2: '其他',
};

export const ACTIVITY_STATUS_TEXT: Readonly<Record<number, string>> = {
  0: '报名中',
  1: '报名截止',
  2: '已结束',
  3: '已取消',
};

export function activityTypeText(type: ActivityType): string {
  return ACTIVITY_TYPE_TEXT[type] ?? '—';
}

export function activityStatusText(status: ActivityStatus): string {
  return ACTIVITY_STATUS_TEXT[status] ?? '—';
}

/** ACT-10 状态合法目标集：0↔1、0/1→2/3、2→0/1、3→0/1。 */
export const ACTIVITY_STATUS_TARGETS: Readonly<
  Record<ActivityStatus, readonly ActivityStatus[]>
> = {
  0: [1, 2, 3],
  1: [0, 2, 3],
  2: [0, 1],
  3: [0, 1],
};

