import type { AttendanceRow, RegistrationRow } from '@yunhefc/shared';

export interface RosterEntry {
  row: RegistrationRow;
  attendance: AttendanceRow | null;
  /** 候补顺位，仅正式候补段有值。 */
  waitlistOrder: number | null;
}

export interface RosterSections {
  formalConfirmed: RosterEntry[];
  formalWaitlist: RosterEntry[];
  guestConfirmed: RosterEntry[];
  total: number;
}

export type RosterGroup = 'formal' | 'waitlist' | 'guest';

export interface RosterSnapshotEntry {
  playerId: number;
  name: string;
  group: RosterGroup;
}

export type RosterSnapshot = RosterSnapshotEntry[];

export interface RosterDiff {
  changed: boolean;
  promoted: string[];
  removed: string[];
  added: string[];
  text: string;
}

export function emptyRoster(): RosterSections {
  return { formalConfirmed: [], formalWaitlist: [], guestConfirmed: [], total: 0 };
}

/**
 * REG-05 的 confirmed（status=0）与 waitlist（status=1）按 type 分段，
 * 再与 ATT-03 按 playerId 左连接；缺考勤记录时 attendance 为 null。
 */
export function buildRoster(
  confirmed: RegistrationRow[],
  waitlist: RegistrationRow[],
  attendance: AttendanceRow[],
): RosterSections {
  const attendanceByPlayer = new Map(attendance.map((item) => [item.player.id, item]));
  const seen = new Set<number>();

  const confirmedEntries = confirmed
    .filter((row) => row.status === 0)
    .filter((row) => {
      if (seen.has(row.player.id)) {
        return false;
      }
      seen.add(row.player.id);
      return true;
    })
    .map((row) => ({
      row,
      attendance: attendanceByPlayer.get(row.player.id) ?? null,
      waitlistOrder: null,
    }));

  const waitlistEntries = waitlist
    .filter((row) => row.status === 1)
    .filter((row) => {
      if (seen.has(row.player.id)) {
        return false;
      }
      seen.add(row.player.id);
      return true;
    })
    .map((row, index) => ({
      row,
      attendance: attendanceByPlayer.get(row.player.id) ?? null,
      waitlistOrder: index + 1,
    }));

  const formalConfirmed = confirmedEntries.filter((entry) => entry.row.type === 1);
  const guestConfirmed = confirmedEntries.filter((entry) => entry.row.type === 0);

  return {
    formalConfirmed,
    formalWaitlist: waitlistEntries,
    guestConfirmed,
    total: formalConfirmed.length + waitlistEntries.length + guestConfirmed.length,
  };
}

export function rosterSnapshot(sections: RosterSections): RosterSnapshot {
  return [
    ...sections.formalConfirmed.map((entry) => ({
      playerId: entry.row.player.id,
      name: entry.row.player.name,
      group: 'formal' as const,
    })),
    ...sections.formalWaitlist.map((entry) => ({
      playerId: entry.row.player.id,
      name: entry.row.player.name,
      group: 'waitlist' as const,
    })),
    ...sections.guestConfirmed.map((entry) => ({
      playerId: entry.row.player.id,
      name: entry.row.player.name,
      group: 'guest' as const,
    })),
  ];
}

/** 从 B03 返回后对比名单，生成「已移除 / 已递补 / 已加入」提示。 */
export function rosterDiff(previous: RosterSnapshot | null, next: RosterSections): RosterDiff {
  if (!previous) {
    return { changed: false, promoted: [], removed: [], added: [], text: '' };
  }
  const nextSnapshot = rosterSnapshot(next);
  const previousByPlayer = new Map(previous.map((entry) => [entry.playerId, entry]));
  const nextByPlayer = new Map(nextSnapshot.map((entry) => [entry.playerId, entry]));

  const promoted = nextSnapshot
    .filter(
      (entry) =>
        entry.group === 'formal' && previousByPlayer.get(entry.playerId)?.group === 'waitlist',
    )
    .map((entry) => entry.name);
  const removed = previous
    .filter((entry) => !nextByPlayer.has(entry.playerId))
    .map((entry) => entry.name);
  const added = nextSnapshot
    .filter((entry) => !previousByPlayer.has(entry.playerId))
    .map((entry) => entry.name);

  const parts: string[] = [];
  if (removed.length) {
    parts.push(`${removed.join('、')} 已移除`);
  }
  if (promoted.length) {
    parts.push(`${promoted.join('、')} 已递补为正式报名`);
  }
  if (added.length) {
    parts.push(`${added.join('、')} 已加入名单`);
  }

  return {
    changed: parts.length > 0,
    promoted,
    removed,
    added,
    text: parts.length ? `名单已更新：${parts.join('；')}。` : '',
  };
}
