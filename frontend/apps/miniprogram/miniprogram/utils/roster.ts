// 移植自 Web features/match-detail/roster.ts；小程序不做 B 端往返，因此无快照/差异。
import type { AttendanceRow, RegistrationRow } from '../types/api';

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

export function emptyRoster(): RosterSections {
  return { formalConfirmed: [], formalWaitlist: [], guestConfirmed: [], total: 0 };
}

/** confirmed(status=0) 按 type 拆正式/外挂；waitlist(status=1) 带顺位；按 playerId 左连接考勤。 */
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
