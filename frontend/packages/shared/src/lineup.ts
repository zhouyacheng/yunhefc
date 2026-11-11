import {
  MATCH_STATUS_CANCELLED,
  MATCH_STATUS_FINISHED,
  MATCH_STATUS_POSTPONED,
  isInternalMatchType,
  type MatchSide,
  type MatchStatus,
  type MatchType,
} from './match';

/** 05 §4 FormationTemplate.slots。 */
export interface FormationSlot {
  slotKey: string;
  role: number;
  roleLabel: string;
  x: number;
  y: number;
}

/** 05 §4 FormationTemplate；预置模板不可改删。 */
export interface FormationTemplate {
  id: number;
  name: string;
  isPreset: boolean;
  baseTemplateId?: number | null;
  slots: FormationSlot[];
}

/** LIN-05 不分页集合。 */
export interface FormationTemplateListResponse {
  items: FormationTemplate[];
}

export interface FormationTemplateResponse {
  template: FormationTemplate;
}

export interface FormationTemplateCreateRequest {
  baseTemplateId: number;
  name: string;
}

export interface FormationTemplateRenameRequest {
  name: string;
}

/** 0-HOME（主队）；1-AWAY（客队）。 */
export type LineupSide = 0 | 1;

/** 05 §4 LineupView 的球员引用。 */
export interface LineupPlayer {
  id: number;
  name: string;
  jersey?: number | null;
  position: number;
  positionLabel?: string | null;
}

export interface LineupStarter {
  slotKey: string;
  player?: LineupPlayer | null;
}

/** LIN-01 只读阵容；对手方 starters 仅空槽位、bench 为空。 */
export interface LineupView {
  side: LineupSide;
  formation: FormationTemplate;
  starters: LineupStarter[];
  bench: LineupPlayer[];
}

/** LIN-02 管理端阵容；未保存时 version=0。 */
export interface LineupAdmin extends LineupView {
  version: number;
  updatedAt?: string | null;
  updatedBy?: number | null;
}

export interface LineupSlotInput {
  slotKey: string;
  playerId?: number | null;
}

/** LIN-03 请求体；我方首发 8 人且 playerId 必填，对手方允许空槽位。 */
export interface LineupSaveRequest {
  formationTemplateId: number;
  starters: LineupSlotInput[];
  bench: number[];
  version: number;
}

export interface LineupSaveResponse {
  version: number;
}

export interface LineupTemplateRequest {
  name: string;
}

export interface LineupTemplateResponse {
  templateId: number;
}

/**
 * 对外比赛中非我方一侧仅记录阵型与空槽位；队内/训练赛两边都属我方。
 */
export function isOpponentLineupSide(
  match: { type?: MatchType | null; ourSide?: MatchSide | null } | null | undefined,
  side: LineupSide,
): boolean {
  if (
    !match ||
    match.type === null ||
    match.type === undefined ||
    isInternalMatchType(match.type)
  ) {
    return false;
  }
  return side !== (match.ourSide ?? 0);
}

/** 非终态且未延期可编辑；与 02 §8.1 一致。 */
export function lineupEditable(status: MatchStatus | null | undefined): boolean {
  return (
    status !== null &&
    status !== undefined &&
    status !== MATCH_STATUS_FINISHED &&
    status !== MATCH_STATUS_POSTPONED &&
    status !== MATCH_STATUS_CANCELLED
  );
}
