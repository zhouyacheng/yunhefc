(function () {
  const Y = window.YHFC;
  const { state, save, currentUser, playerById, addAudit, notify, statusLabel, formatDateTime, seasonKeyOf } = Y;
  if (!state || typeof save !== 'function') throw new Error('match.js 需要在 core.js 之后加载');

  function matchSnapshot(match) {
    const fields = ['title', 'type', 'start', 'end', 'signupDeadline', 'cancelLock', 'venue',
      'competition', 'season', 'round', 'homeTeamName', 'awayTeamName', 'opponent', 'ourSide',
      'status', 'cancelReason', 'capacity', 'guestCapacity', 'waitlistCapacity', 'includeGuests', 'manualStatus'];
    const snapshot = {};
    fields.forEach((field) => { snapshot[field] = match[field]; });
    return snapshot;
  }

  function notifyRegistrants(match, title, body) {
    const targets = new Set(
      (match.registrations || [])
        .filter((registration) => ['CONFIRMED', 'WAITLISTED'].includes(registration.status))
        .map((registration) => registration.playerId)
    );
    targets.forEach((playerId) => notify(playerId, title, body));
  }

  function deriveAutoStatus(match, now) {
    if (!match.start || !match.end) return match.status;
    if (now >= new Date(match.end).getTime()) return 'FINISHED';
    if (now >= new Date(match.start).getTime()) return 'IN_PROGRESS';
    if (match.cancelLock && now >= new Date(match.cancelLock).getTime()) return 'LOCKED';
    if (match.signupDeadline && now >= new Date(match.signupDeadline).getTime()) return 'SIGNUP_CLOSED';
    return 'SIGNUP_OPEN';
  }

  function autoTransitionMatches(matches) {
    let changed = 0;
    const now = Date.now();
    for (const match of matches || []) {
      if (match.manualStatus || ['CANCELLED', 'POSTPONED'].includes(match.status)) continue;
      const next = deriveAutoStatus(match, now);
      if (next === match.status) continue;
      if ((Y.STATUS_RANK[next] ?? -1) <= (Y.STATUS_RANK[match.status] ?? -1)) continue;
      match.status = next;
      match.statusUpdatedAt = new Date().toISOString();
      match.statusUpdatedBy = '系统';
      addAudit('系统', '自动流转', `${match.title} → ${statusLabel(next)}`);
      notifyRegistrants(match, '比赛状态变更', `「${match.title}」状态变为${statusLabel(next)}。`);
      changed += 1;
    }
    return changed;
  }

  const STATUS_TRANSITIONS = {
    SIGNUP_OPEN: ['SIGNUP_CLOSED', 'LOCKED'],
    SIGNUP_CLOSED: ['LOCKED'],
    LOCKED: ['IN_PROGRESS'],
    IN_PROGRESS: ['FINISHED'],
    FINISHED: [],
    CANCELLED: [],
    POSTPONED: []
  };
  const STATUS_OPTIONS = ['SIGNUP_OPEN', 'SIGNUP_CLOSED', 'LOCKED', 'IN_PROGRESS', 'FINISHED', 'POSTPONED', 'CANCELLED'];

  function statusOptions(match) {
    return STATUS_OPTIONS.map((status) => ({ status, disabled: false, reason: '' }));
  }

  function setMatchStatus(match, nextStatus, actorName, options) {
    if (!Y.hasPermission('match:status:any')) return { ok: false, message: '没有变更比赛状态的权限' };
    if (!match) return { ok: false, message: '比赛不存在' };
    if (match.status === nextStatus) {
      if (options && options.manual === false) {
        match.manualStatus = false;
        save();
        addAudit(actorName || '管理员', '恢复自动流转', match.title);
        return { ok: true };
      }
      return { ok: false, message: '状态未变化' };
    }
    if (nextStatus === 'POSTPONED' || nextStatus === 'CANCELLED') {
      return { ok: false, message: nextStatus === 'POSTPONED' ? '请使用延期操作修改时间' : '请使用取消比赛操作并填写原因' };
    }
    const before = matchSnapshot(match);
    match.status = nextStatus;
    match.manualStatus = !options || options.manual !== false;
    match.statusUpdatedAt = new Date().toISOString();
    match.statusUpdatedBy = actorName || '管理员';
    save();
    notifyRegistrants(match, '比赛状态变更', `「${match.title}」状态变为${statusLabel(nextStatus)}。`);
    addAudit(actorName || '管理员', '变更比赛状态', `${match.title} → ${statusLabel(nextStatus)}`, { before, after: matchSnapshot(match) });
    return { ok: true };
  }

  function cancelMatch(match, reason, actorName) {
    if (!Y.hasPermission('match:status:any')) return { ok: false, message: '没有取消比赛的权限' };
    if (!match) return { ok: false, message: '比赛不存在' };
    if (!reason || !String(reason).trim()) return { ok: false, message: '请填写取消原因' };
    const before = matchSnapshot(match);
    match.status = 'CANCELLED';
    match.manualStatus = true;
    match.cancelReason = String(reason).trim();
    match.statusUpdatedAt = new Date().toISOString();
    match.statusUpdatedBy = actorName || '管理员';
    save();
    notifyRegistrants(match, '比赛取消', `「${match.title}」已取消：${match.cancelReason}`);
    addAudit(actorName || '管理员', '取消比赛', `${match.title} / ${match.cancelReason}`, { before, after: matchSnapshot(match) });
    return { ok: true };
  }

  function postponeMatch(match, changes, actorName) {
    if (!Y.hasPermission('match:edit:any')) return { ok: false, message: '没有编辑比赛的权限' };
    if (!match) return { ok: false, message: '比赛不存在' };
    if (match.status === 'POSTPONED') return { ok: false, message: '已延期的比赛请使用重新排期' };
    const before = matchSnapshot(match);
    match.start = changes.start;
    match.end = changes.end;
    match.signupDeadline = changes.signupDeadline;
    match.cancelLock = changes.cancelLock;
    match.status = 'POSTPONED';
    match.manualStatus = true;
    match.postponedAt = new Date().toISOString();
    match.postponedBy = actorName || '管理员';
    match.postponeCount = (match.postponeCount || 0) + 1;
    save();
    notifyRegistrants(match, '比赛延期', `「${match.title}」已延期，等待重新排期。`);
    addAudit(actorName || '管理员', '延期比赛', match.title, { before, after: matchSnapshot(match) });
    return { ok: true };
  }

  function rescheduleMatch(match, changes, actorName) {
    if (!Y.hasPermission('match:edit:any')) return { ok: false, message: '没有编辑比赛的权限' };
    if (!match || match.status !== 'POSTPONED') return { ok: false, message: '仅已延期的比赛可以重新排期' };
    const before = matchSnapshot(match);
    match.start = changes.start;
    match.end = changes.end;
    match.signupDeadline = changes.signupDeadline;
    match.cancelLock = changes.cancelLock;
    match.status = 'SIGNUP_OPEN';
    match.manualStatus = false;
    match.rescheduledAt = new Date().toISOString();
    save();
    notifyRegistrants(match, '比赛恢复报名', `「${match.title}」已重新排期至 ${formatDateTime(match.start)}。`);
    addAudit(actorName || '管理员', '重新排期', match.title, { before, after: matchSnapshot(match) });
    return { ok: true };
  }

  function nextOrdinals(matches, season, start) {
    const key = seasonKeyOf({ season, start });
    const seasonMax = Math.max(0, ...(matches || [])
      .filter((match) => seasonKeyOf(match) === key)
      .map((match) => Number(match.seasonMatchNo) || 0));
    const totalMax = Math.max(0, ...(matches || []).map((match) => Number(match.totalMatchNo) || 0));
    return { seasonMatchNo: seasonMax + 1, totalMatchNo: totalMax + 1 };
  }

  function matchesByStartDesc(list) {
    return (list || state.matches).slice().sort((a, b) => new Date(b.start) - new Date(a.start));
  }

  function nextMatch() {
    const upcoming = state.matches
      .filter((match) => ['SIGNUP_OPEN', 'SIGNUP_CLOSED', 'LOCKED', 'IN_PROGRESS', 'POSTPONED'].includes(match.status))
      .sort((a, b) => new Date(a.start) - new Date(b.start));
    return upcoming[0] || null;
  }

  function registrationOf(match, playerId) {
    return (match.registrations || []).find(
      (registration) => registration.playerId === Number(playerId) &&
        ['CONFIRMED', 'WAITLISTED'].includes(registration.status)
    );
  }

  function confirmed(match, type) {
    return (match.registrations || []).filter(
      (registration) => registration.status === 'CONFIRMED' && (!type || registration.type === type)
    );
  }

  function waitlisted(match, type) {
    return (match.registrations || [])
      .filter((registration) => registration.status === 'WAITLISTED' && (!type || registration.type === type))
      .sort((a, b) => new Date(a.at) - new Date(b.at));
  }

  function identityType(player) {
    return player && player.type === 'GUEST' ? 'GUEST' : 'PLAYER';
  }

  function signupGate(match, user) {
    const player = user || currentUser();
    if (!player.id) return { ok: false, message: '请先登录' };
    if (player.status !== 'ACTIVE') return { ok: false, message: '待审核用户暂不能报名' };
    if (!Y.hasPermission('registration:create:own', player)) return { ok: false, message: '没有报名权限' };
    if (match.status !== 'SIGNUP_OPEN') return { ok: false, message: `当前比赛${statusLabel(match.status)}，不可报名` };
    if (new Date(match.signupDeadline) < new Date()) return { ok: false, message: '报名已截止' };
    if (new Date(match.start) <= new Date()) return { ok: false, message: '比赛已开始，不可报名' };
    if (registrationOf(match, player.id)) return { ok: false, message: '你已报名或已在候补' };
    return { ok: true };
  }

  function cancelGate(match, registration) {
    if (!registration) return { ok: false, message: '没有有效报名' };
    if (Y.isTerminalMatch(match) || ['LOCKED', 'POSTPONED'].includes(match.status)) return { ok: false, message: '当前比赛不可取消报名' };
    if (new Date(match.cancelLock) < new Date()) return { ok: false, message: '已过锁定时间，请联系管理员' };
    return { ok: true };
  }

  function signup(match, player) {
    const user = player || currentUser();
    const gate = signupGate(match, user);
    if (!gate.ok) return gate;
    const type = identityType(user);
    const settings = state.settings;
    const capacity = type === 'GUEST'
      ? (match.guestCapacity ?? settings.maxGuests)
      : (match.capacity ?? settings.maxPlayers);
    const waitlistCapacity = match.waitlistCapacity ?? settings.maxWaitlist;
    const confirmedCount = confirmed(match, type).length;
    const waitlistCount = waitlisted(match, 'PLAYER').length;
    let status;
    if (confirmedCount < capacity) {
      status = 'CONFIRMED';
    } else if (type === 'PLAYER' && waitlistCount < waitlistCapacity) {
      status = 'WAITLISTED';
    } else {
      return { ok: false, message: type === 'GUEST' ? '外挂名额已满' : '名额与候补均已满' };
    }
    if (!match.registrations) match.registrations = [];
    match.registrations.push({ playerId: user.id, type, status, at: new Date().toISOString() });
    save();
    notify(user.id, status === 'CONFIRMED' ? '报名成功' : '进入候补',
      status === 'CONFIRMED' ? `你已成功报名「${match.title}」。` : `名额已满，你已进入「${match.title}」候补。`);
    addAudit(user.name, status === 'CONFIRMED' ? '报名' : '进入候补', match.title);
    return { ok: true, status };
  }

  function promote(match, type) {
    const next = waitlisted(match, type)[0];
    if (!next) return null;
    next.status = 'CONFIRMED';
    notify(next.playerId, '候补递补', `你已从候补转为正式报名「${match.title}」。`);
    addAudit('系统', '候补递补', `${match.title} / ${playerById(next.playerId)?.name || next.playerId}`);
    save();
    return next;
  }

  function cancelSignup(match, playerId) {
    const registration = registrationOf(match, playerId);
    const gate = cancelGate(match, registration);
    if (!gate.ok) return gate;
    const wasConfirmed = registration.status === 'CONFIRMED';
    registration.status = 'CANCELLED';
    if (wasConfirmed) promote(match, registration.type);
    save();
    addAudit(currentUser().name, '取消报名', match.title);
    return { ok: true, wasConfirmed };
  }

  function removeRegistration(match, playerId, actorName) {
    if (!Y.hasPermission('registration:remove:any')) return { ok: false, message: '没有移除报名的权限' };
    if (Y.isTerminalMatch(match)) return { ok: false, message: '已取消或已结束的比赛不能移除报名' };
    const registration = registrationOf(match, playerId);
    if (!registration) return { ok: false, message: '没有有效报名' };
    const wasConfirmed = registration.status === 'CONFIRMED';
    registration.status = 'REMOVED';
    const promoted = wasConfirmed ? Boolean(promote(match, registration.type)) : false;
    notify(playerId, '报名已移除', `你的报名已从「${match.title}」移除。`);
    save();
    addAudit(actorName || currentUser().name || '管理员', '移除报名', `${match.title} / ${playerById(playerId)?.name || playerId}`);
    return { ok: true, promoted };
  }

  function cancelPlayerRegistrations(playerId, actorName) {
    let cancelled = 0;
    state.matches.forEach((match) => {
      if (Y.isTerminalMatch(match) || match.status === 'IN_PROGRESS') return;
      const registration = (match.registrations || []).find(
        (item) => Number(item.playerId) === Number(playerId) && ['CONFIRMED', 'WAITLISTED'].includes(item.status)
      );
      if (!registration) return;
      const wasConfirmed = registration.status === 'CONFIRMED';
      registration.status = 'CANCELLED';
      if (wasConfirmed) promote(match, registration.type);
      cancelled += 1;
    });
    if (cancelled) {
      save();
      addAudit(actorName || '管理员', '注销取消报名', `${playerById(playerId)?.name || playerId} / ${cancelled} 场`);
    }
    return cancelled;
  }

  function attendanceOf(match, playerId) {
    return (match.attendance || []).find((item) => item.playerId === Number(playerId));
  }

  function markAttendance(match, playerId, status, source, actorName) {
    if (source !== 'SELF' && !Y.hasPermission('attendance:mark:any')) return { ok: false, message: '没有标记考勤的权限' };
    if (['CANCELLED', 'POSTPONED'].includes(match.status)) return { ok: false, message: '已取消或已延期的比赛不记考勤' };
    if (!Y.ATTENDANCE_STATUSES.includes(status)) return { ok: false, message: '考勤状态无效' };
    if (status === 'TEMP_PRESENT') {
      const target = playerById(playerId);
      if (!target || target.status !== 'ACTIVE') return { ok: false, message: '仅正常状态球员可标记临时到场' };
      const confirmedReg = (match.registrations || []).some(
        (item) => Number(item.playerId) === Number(playerId) && item.status === 'CONFIRMED'
      );
      if (confirmedReg) return { ok: false, message: '已确认报名的球员不能标记临时到场' };
    }
    if (!match.attendance) match.attendance = [];
    const existing = attendanceOf(match, playerId);
    if (existing) {
      existing.status = status;
      existing.source = source || 'ADMIN';
    } else {
      match.attendance.push({ playerId: Number(playerId), status, source: source || 'ADMIN' });
    }
    save();
    addAudit(actorName || currentUser().name || '管理员', '标记考勤',
      `${match.title} / ${playerById(playerId)?.name || playerId} / ${status}`);
    return { ok: true };
  }

  function checkInWindow(match) {
    const start = new Date(match.start).getTime() - 4 * 60 * 60 * 1000;
    const end = new Date(match.start);
    end.setHours(23, 59, 59, 999);
    const now = Date.now();
    return { start, end: end.getTime(), ok: now >= start && now <= end.getTime() };
  }

  function checkIn(match, playerId) {
    if (!Y.hasPermission('attendance:checkin:own')) return { ok: false, message: '没有签到权限' };
    if (['CANCELLED', 'POSTPONED'].includes(match.status)) return { ok: false, message: '已取消或已延期的比赛不能签到' };
    if (Number(currentUser().id) !== Number(playerId)) return { ok: false, message: '只能为自己签到' };
    const registration = (match.registrations || []).find(
      (item) => Number(item.playerId) === Number(playerId) && item.status === 'CONFIRMED'
    );
    if (!registration) return { ok: false, message: '仅已确认报名的球员可签到' };
    const window = checkInWindow(match);
    if (!window.ok) return { ok: false, message: '签到窗口为开赛前 4 小时至比赛当天 23:59' };
    markAttendance(match, playerId, 'PRESENT', 'SELF', currentUser().name);
    return { ok: true };
  }

  function autoMarkAbsences(matches) {
    let changed = 0;
    const now = Date.now();
    for (const match of matches || []) {
      if (match.status !== 'FINISHED' || !match.end) continue;
      const actualEnd = match.statusUpdatedAt ? new Date(match.statusUpdatedAt) : new Date(match.end);
      if (now < actualEnd.getTime() + 6 * 60 * 60 * 1000) continue;
      if (!match.attendance) match.attendance = [];
      (match.registrations || []).forEach((registration) => {
        if (registration.status !== 'CONFIRMED') return;
        if (match.attendance.some((item) => Number(item.playerId) === Number(registration.playerId))) return;
        match.attendance.push({ playerId: Number(registration.playerId), status: 'ABSENT', source: 'SYSTEM' });
        changed += 1;
      });
    }
    return changed;
  }

  Object.assign(Y, {
    matchSnapshot, notifyRegistrants, deriveAutoStatus, autoTransitionMatches, setMatchStatus, STATUS_TRANSITIONS, statusOptions,
    cancelMatch, postponeMatch, rescheduleMatch, nextOrdinals, matchesByStartDesc, nextMatch, registrationOf, confirmed, waitlisted, identityType,
    signupGate, cancelGate, signup, promote, cancelSignup, removeRegistration, cancelPlayerRegistrations, attendanceOf,
    markAttendance, checkInWindow, checkIn, autoMarkAbsences
  });
})();
