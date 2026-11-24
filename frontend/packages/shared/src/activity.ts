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
  costPerPerson: number | null;
  location: string;
  locationImageUrl: string | null;
  description: string | null;
  status: ActivityStatus;
  statusLabel: string;
  signupCount: number;
  signupOpen: boolean;
  /** 仅登录时返回；对齐 05 §4 与 MatchDetail.myRegistration 的可选口径。 */
  mySignup?: ActivitySignup | null;
}

export type ActivityDetail = ActivitySummary;

/** ACT-06/07/08/09/10：管理端额外返回 version 与创建人。 */
export interface ActivityAdmin extends ActivitySummary {
  version: number;
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
