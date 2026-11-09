(function () {
  const Y = window.YHFC;
  const { save, currentUser, userRole, playerById, teamNames, escapeHtml, clone,
    EVENT_TYPES, eventTypeLabel, openModal, closeModal, toast, addAudit,
    confirmed, INTERNAL_TYPES } = Y;
  if (typeof confirmed !== 'function') throw new Error('events.js 需要在 match.js 之后加载');

  function activeEvents(match) {
    return (match.events || []).filter((event) => !event.deletedAt);
  }

  function sanitizeEvent(event) {
    const clean = {
      type: event.type,
      team: event.team === 'AWAY' ? 'AWAY' : 'HOME',
      half: event.half === 'SECOND' ? 'SECOND' : 'FIRST',
      minute: Number(event.minute)
    };
    if (event.type === 'GOAL') {
      clean.playerId = Number(event.playerId) || null;
      clean.assistId = event.assistId ? Number(event.assistId) : null;
      clean.penalty = Boolean(event.penalty);
    } else if (event.type === 'SUBSTITUTION') {
      clean.inId = Number(event.inId) || null;
      clean.outId = Number(event.outId) || null;
    } else {
      clean.playerId = Number(event.playerId) || null;
    }
    return clean;
  }

  function eventTeamRoster(match, team) {
    const roster = confirmed(match).map((registration) => playerById(registration.playerId)).filter(Boolean);
    if (!INTERNAL_TYPES.includes(match.type)) {
      return team === Y.ourLineupSide(match) ? roster : [];
    }
    if (match.grouping) {
      const ids = new Set((match.grouping[Y.groupingTeamForSide(team)] || []).map(Number));
      return roster.filter((player) => ids.has(Number(player.id)));
    }
    return roster;
  }

  function validateEventForTeam(match, event) {
    const rosterIds = new Set(eventTeamRoster(match, event.team).map((player) => Number(player.id)));
    const ids = [event.playerId, event.assistId, event.inId, event.outId]
      .map(Number).filter(Boolean);
    if (ids.some((id) => !rosterIds.has(id))) {
      const grouped = INTERNAL_TYPES.includes(match.type) && match.grouping;
      return grouped
        ? '主队/客队只能选择各自分组成员'
        : (INTERNAL_TYPES.includes(match.type) ? '只能选择本场确认报名球员' : '只能选择我方球员');
    }
    return '';
  }

  function validateEvent(event) {
    if (!EVENT_TYPES.includes(event.type)) return '事件类型无效';
    if (!Number.isInteger(Number(event.minute)) || Number(event.minute) < 1 || Number(event.minute) > 120) {
      return '必须填写事件时间（1–120 分钟）';
    }
    if (!['FIRST', 'SECOND'].includes(event.half)) return '必须选择上半场或下半场';
    if (event.type === 'SUBSTITUTION') {
      if (!event.inId || !event.outId || Number(event.inId) === Number(event.outId)) {
        return '换人事件必须选择不同的换上/换下球员';
      }
    } else if (!event.playerId) {
      return '必须选择球员';
    }
    if (event.type === 'GOAL' && event.assistId && Number(event.assistId) === Number(event.playerId)) {
      return '助攻球员不能是进球球员本人';
    }
    return '';
  }

  function addEvent(match, event, actorName) {
    if (!Y.hasPermission('event:create:own') && !Y.hasPermission('event:edit:any')) {
      return { ok: false, message: '没有录入事件的权限' };
    }
    if (!Y.eventEntryOpen(match)) {
      return { ok: false, message: '仅进行中或已结束的比赛可录入事件' };
    }
    if (!Y.hasPermission('event:edit:any') && event.type === 'SUBSTITUTION') {
      return { ok: false, message: '换人事件仅管理员可录入' };
    }
    if (!Y.hasPermission('event:edit:any') && Date.now() > eventEditWindowEndsAt(match)) {
      return { ok: false, message: '已超过可录入时间（赛后 24 小时）' };
    }
    const actor = currentUser();
    if (!Y.hasPermission('event:edit:any', actor) && !Y.INTERNAL_TYPES.includes(match.type)) {
      if (Number(event.playerId) !== Number(actor.id)) {
        return { ok: false, message: '只能录入自己的事件' };
      }
      if (event.team && event.team !== Y.ourLineupSide(match)) {
        return { ok: false, message: '只能录入我方球队的事件' };
      }
    }
    const message = validateEvent(event);
    if (message) return { ok: false, message };
    const teamMessage = validateEventForTeam(match, sanitizeEvent(event));
    if (teamMessage) return { ok: false, message: teamMessage };
    const user = currentUser();
    const clean = sanitizeEvent(event);
    if (!match.events) match.events = [];
    const saved = {
      id: 'e' + Date.now() + Math.random().toString(16).slice(2),
      ...clean,
      createdAt: new Date().toISOString(),
      createdBy: user.id || null,
      createdByName: actorName || user.name,
      updatedAt: null,
      updatedBy: null,
      deletedAt: null,
      deletedBy: null
    };
    match.events.push(saved);
    save();
    addAudit(actorName || user.name, '录入事件', match.title, { after: clone(saved), matchId: match.id, eventId: saved.id });
    return { ok: true, event: saved };
  }

  function updateEvent(match, eventId, patch, actorName) {
    const event = (match.events || []).find((item) => item.id === eventId && !item.deletedAt);
    if (!event) return { ok: false, message: '事件不存在' };
    if (['CANCELLED', 'POSTPONED'].includes(match.status)) return { ok: false, message: '已取消或已延期的比赛不能修改事件' };
    const user = currentUser();
    if (!Y.hasPermission('event:edit:any', user)) {
      if (!Y.hasPermission('event:edit:own', user)) return { ok: false, message: '没有权限修改事件' };
      if (event.type === 'SUBSTITUTION' || patch.type === 'SUBSTITUTION') {
        return { ok: false, message: '换人事件仅管理员可修改' };
      }
      if (Date.now() > eventEditWindowEndsAt(match)) {
        return { ok: false, message: '已超过可编辑时间（赛后 24 小时）' };
      }
      if (!canEditEvent(match, event, user)) {
        return { ok: false, message: '只能修改自己的事件' };
      }
      const nextPlayerId = patch.playerId !== undefined ? Number(patch.playerId) : Number(event.playerId);
      if (nextPlayerId !== Number(user.id)) {
        return { ok: false, message: '只能修改自己的事件' };
      }
      if (!Y.INTERNAL_TYPES.includes(match.type) && patch.team !== undefined && patch.team !== Y.ourLineupSide(match)) {
        return { ok: false, message: '只能修改我方球队的事件' };
      }
      if (patch.inId !== undefined || patch.outId !== undefined) {
        return { ok: false, message: '换人事件仅管理员可修改' };
      }
    }
    const merged = { ...event, ...patch };
    const message = validateEvent(merged);
    if (message) return { ok: false, message };
    const teamMessage = validateEventForTeam(match, sanitizeEvent(merged));
    if (teamMessage) return { ok: false, message: teamMessage };
    const before = clone(event);
    const clean = sanitizeEvent(merged);
    ['playerId', 'assistId', 'penalty', 'inId', 'outId'].forEach((key) => { delete event[key]; });
    Object.assign(event, clean, {
      updatedAt: new Date().toISOString(),
      updatedBy: actorName || currentUser().name
    });
    save();
    addAudit(actorName || currentUser().name, '修改事件', match.title, { before, after: clone(event), matchId: match.id, eventId });
    return { ok: true, event };
  }

  function deleteEvent(match, eventId, actorName) {
    if (['CANCELLED', 'POSTPONED'].includes(match.status)) return { ok: false, message: '已取消或已延期的比赛不能删除事件' };
    if (!Y.hasPermission('event:delete:any')) return { ok: false, message: '没有删除事件的权限' };
    const event = (match.events || []).find((item) => item.id === eventId && !item.deletedAt);
    if (!event) return { ok: false, message: '事件不存在' };
    const before = clone(event);
    event.deletedAt = new Date().toISOString();
    event.deletedBy = actorName || currentUser().name;
    save();
    addAudit(actorName || currentUser().name || '管理员', '删除事件', match.title, { before, after: null, matchId: match.id, eventId });
    return { ok: true };
  }

  function eventEditWindowEndsAt(match) {
    const base = new Date(match.end || match.start).getTime();
    return base + 24 * 60 * 60 * 1000;
  }

  function canEditEvent(match, event, user) {
    const actor = user || currentUser();
    if (!event || event.deletedAt) return false;
    if (Y.hasPermission('event:edit:any', actor)) return true;
    if (!Y.hasPermission('event:edit:own', actor)) return false;
    if (event.type === 'SUBSTITUTION') return false;
    if (Date.now() > eventEditWindowEndsAt(match)) return false;
    const owners = [event.playerId, event.inId, event.outId]
      .filter((id) => id != null)
      .map(Number);
    return owners.includes(Number(actor.id));
  }

  function halfLabel(half) {
    if (half === 'SECOND') return '下半场';
    if (half === 'FIRST') return '上半场';
    return '';
  }

  function eventTimeLabel(event) {
    if (!event) return '—';
    const half = halfLabel(event.half);
    const minute = event.minute ? `${event.minute}'` : '';
    if (half && minute) return `${half} ${minute}`;
    return half || minute || '—';
  }

  function compareEvents(a, b) {
    const order = { FIRST: 0, SECOND: 1 };
    const halfA = order[a.half] == null ? 0 : order[a.half];
    const halfB = order[b.half] == null ? 0 : order[b.half];
    if (halfA !== halfB) return halfA - halfB;
    const minuteA = a.minute || 0;
    const minuteB = b.minute || 0;
    if (minuteA !== minuteB) return minuteA - minuteB;
    const goalRank = (event) => ['GOAL', 'OWN_GOAL'].includes(event.type) ? 0 : 1;
    return goalRank(a) - goalRank(b);
  }

  function derivedScore(match) {
    let home = 0;
    let away = 0;
    for (const event of activeEvents(match)) {
      const isHome = event.team !== 'AWAY';
      if (event.type === 'GOAL') {
        if (isHome) home += 1;
        else away += 1;
      }
      if (event.type === 'OWN_GOAL') {
        if (isHome) away += 1;
        else home += 1;
      }
    }
    const ours = match.ourSide === 'AWAY' ? away : home;
    const opponent = match.ourSide === 'AWAY' ? home : away;
    return { home, away, ours, opponent };
  }

  function renderEventTimeline(match, events, options) {
    const admin = Boolean(options && options.admin);
    const canEdit = options && typeof options.canEdit === 'function' ? options.canEdit : () => false;
    const names = teamNames(match);
    const rows = (events || [])
      .slice()
      .sort(compareEvents)
      .map((event) => {
        const player = event.playerId ? playerById(event.playerId) : null;
        const assist = event.assistId ? playerById(event.assistId) : null;
        const inPlayer = event.inId ? playerById(event.inId) : null;
        const outPlayer = event.outId ? playerById(event.outId) : null;
        const meta = {
          GOAL: ['⚽', '进球'],
          OWN_GOAL: ['🙃', '乌龙球'],
          SUBSTITUTION: ['🔁', '换人'],
          YELLOW_CARD: ['🟨', '黄牌'],
          RED_CARD: ['🟥', '红牌']
        }[event.type] || ['•', event.type];
        const detail = event.type === 'SUBSTITUTION'
          ? `${escapeHtml(inPlayer?.name || '—')} 换下 ${escapeHtml(outPlayer?.name || '—')}`
          : `${escapeHtml(player?.name || '—')}${assist ? ` · 助攻 ${escapeHtml(assist.name)}` : ''}${event.penalty ? ' · 点球' : ''}`;
        const side = event.team === 'AWAY' ? 'away' : 'home';
        const card = `<div class="event-card">
          <div class="event-card-head">
            <span class="event-icon">${meta[0]}</span>
            <strong>${meta[1]}</strong>
            ${canEdit(event) ? `<button class="icon-btn" data-edit-event="${event.id}" title="编辑">✎</button>` : ''}
            ${admin ? `<button class="icon-btn danger" data-delete-event="${event.id}" title="删除">×</button>` : ''}
          </div>
          <p>${detail}</p>
        </div>`;
        return `<div class="timeline-event ${side}">
          <div class="event-side event-left">${side === 'home' ? card : ''}</div>
          <div class="event-axis"><span class="event-time">${eventTimeLabel(event)}</span><span class="event-dot"></span></div>
          <div class="event-side event-right">${side === 'away' ? card : ''}</div>
        </div>`;
      })
      .join('');
    return `<div class="team-timeline">
      <div class="team-timeline-head">
        <span class="home-name">${escapeHtml(names.home)}</span>
        <span class="axis-label">时间</span>
        <span class="away-name">${escapeHtml(names.away)}</span>
      </div>
      <div class="team-timeline-body">${rows}</div>
    </div>`;
  }

  function openEventModal(match, existing, options) {
    const admin = Y.hasPermission('event:edit:any');
    const user = currentUser();
    if (existing && !canEditEvent(match, existing, user)) {
      toast('没有权限修改该事件或已超过可编辑时间');
      return;
    }
    if (existing && existing.type === 'SUBSTITUTION' && !admin) {
      toast('换人事件仅管理员可修改');
      return;
    }
    const internalMatch = INTERNAL_TYPES.includes(match.type);
    const canUseBothTeams = admin || internalMatch;
    const roster = confirmed(match).map((registration) => playerById(registration.playerId)).filter(Boolean);
    if (!roster.length) {
      toast('当前名单中没有可录入事件的球员');
      return;
    }
    const allowedTypes = admin ? EVENT_TYPES : EVENT_TYPES.filter((type) => type !== 'SUBSTITUTION');
    const typeOptions = allowedTypes.map((value) => [value, eventTypeLabel(value)]);
    const optionHtml = (list) => list.map((player) => `<option value="${player.id}">${escapeHtml(player.name)} #${player.jersey}</option>`).join('');
    const teamRoster = (team) => eventTeamRoster(match, team);
    const names = teamNames(match);
    let defaultTeam = 'HOME';
    if (existing) defaultTeam = existing.team || 'HOME';
    else if (!admin) {
      if (internalMatch && match.grouping) {
        defaultTeam = (match.grouping.B || []).includes(user.id) ? 'AWAY' : 'HOME';
      } else {
        defaultTeam = Y.ourLineupSide(match);
      }
    } else {
      defaultTeam = internalMatch ? 'HOME' : Y.ourLineupSide(match);
    }
    const teamLocked = !canUseBothTeams;
    const initialRoster = teamRoster(defaultTeam);
    const initialPlayers = canUseBothTeams ? initialRoster : initialRoster.filter((player) => player.id === user.id);
    const teamOptions = `<option value="HOME" ${defaultTeam === 'HOME' ? 'selected' : ''}>主队 · ${escapeHtml(names.home)}</option><option value="AWAY" ${defaultTeam === 'AWAY' ? 'selected' : ''}>客队 · ${escapeHtml(names.away)}</option>`;
    const body = `<form id="event-form" class="form-grid">
      <label>事件类型<select name="type" class="input">${typeOptions.map(([value, label]) => `<option value="${value}">${label}</option>`).join('')}</select></label>
      <label>球员<select name="playerId" class="input" ${canUseBothTeams ? '' : 'disabled'}>${optionHtml(initialPlayers) || '<option value="">该队暂无球员</option>'}</select></label>
      <label>所属球队<select name="team" class="input" ${teamLocked ? 'disabled' : ''}>${teamOptions}</select></label>
      <label>半场<select name="half" class="input"><option value="FIRST">上半场</option><option value="SECOND">下半场</option></select></label>
      <label>时间（分钟）<input name="minute" type="number" min="1" max="120" class="input" required placeholder="例如 23" value="${existing && existing.minute ? existing.minute : ''}"></label>
      <label data-goal-field hidden>助攻（进球可选）<select name="assistId" class="input"><option value="">无</option>${optionHtml(initialRoster)}</select></label>
      <div class="full event-sub-fields" data-sub-fields hidden>
        <label>换上<select name="inId" class="input" disabled><option value="">—</option>${optionHtml(initialRoster)}</select></label>
        <label>换下<select name="outId" class="input" disabled><option value="">—</option>${optionHtml(initialRoster)}</select></label>
      </div>
      <label class="checkline" data-goal-field hidden><input name="penalty" type="checkbox"> 点球</label>
      <p class="muted small full">${admin ? '管理员可录入任意球员的事件；换人事件仅管理员可录入。' : internalMatch ? '队内赛可为主队/客队添加事件；换人由管理员录入。' : '球员只能录入自己的事件；换人由管理员录入。'}</p>
    </form>`;
    const modal = openModal(existing ? '编辑比赛事件' : '录入比赛事件', body, '<button class="btn btn-primary" id="event-submit">保存事件</button><button class="btn btn-ghost" data-close-modal>取消</button>');
    const teamSelect = modal.querySelector('[name="team"]');
    const playerSelect = modal.querySelector('[name="playerId"]');
    const assistSelect = modal.querySelector('[name="assistId"]');
    const fillTeam = (team, keep) => {
      const list = teamRoster(team);
      const scoped = canUseBothTeams ? list : list.filter((player) => player.id === user.id);
      playerSelect.innerHTML = optionHtml(scoped) || '<option value="">该队暂无球员</option>';
      assistSelect.innerHTML = `<option value="">无</option>${optionHtml(list)}`;
      const inSelect = modal.querySelector('[name="inId"]');
      const outSelect = modal.querySelector('[name="outId"]');
      const lineup = match.lineups && match.lineups[team];
      const teamIds = new Set(list.map((player) => Number(player.id)));
      const benchList = ((lineup && lineup.bench) || []).map((id) => playerById(id))
        .filter((player) => player && teamIds.has(Number(player.id)));
      const starterList = Object.values((lineup && lineup.starters) || {}).map((id) => playerById(id))
        .filter((player) => player && teamIds.has(Number(player.id)));
      if (inSelect) inSelect.innerHTML = `<option value="">—</option>${optionHtml(benchList)}`;
      if (outSelect) outSelect.innerHTML = `<option value="">—</option>${optionHtml(starterList)}`;
      if (keep) {
        playerSelect.value = keep.playerId || playerSelect.value;
        assistSelect.value = keep.assistId || '';
        if (inSelect) inSelect.value = keep.inId || '';
        if (outSelect) outSelect.value = keep.outId || '';
      }
    };
    teamSelect.addEventListener('change', () => fillTeam(teamSelect.value));
    if (!existing) fillTeam(defaultTeam);
    if (existing) {
      teamSelect.value = existing.team || defaultTeam;
      fillTeam(teamSelect.value, existing);
      modal.querySelector('[name="type"]').value = existing.type;
      modal.querySelector('[name="half"]').value = existing.half || 'FIRST';
      const penalty = modal.querySelector('[name="penalty"]');
      if (penalty) penalty.checked = Boolean(existing.penalty);
    }
    const typeSelect = modal.querySelector('[name="type"]');
    const subFields = modal.querySelector('[data-sub-fields]');
    const inSelect = modal.querySelector('[name="inId"]');
    const outSelect = modal.querySelector('[name="outId"]');
    const goalFields = Array.from(modal.querySelectorAll('[data-goal-field]'));
    const penaltyInput = modal.querySelector('[name="penalty"]');
    const toggleSubFields = () => {
      const isSubstitution = typeSelect.value === 'SUBSTITUTION';
      const isGoal = typeSelect.value === 'GOAL';
      subFields.hidden = !isSubstitution;
      inSelect.disabled = !isSubstitution;
      outSelect.disabled = !isSubstitution;
      goalFields.forEach((field) => { field.hidden = !isGoal; });
      if (!isSubstitution) {
        inSelect.value = '';
        outSelect.value = '';
      }
      if (!isGoal) {
        assistSelect.value = '';
        penaltyInput.checked = false;
      }
    };
    typeSelect.addEventListener('change', toggleSubFields);
    toggleSubFields();
    modal.querySelector('#event-submit').addEventListener('click', () => {
      const form = modal.querySelector('#event-form');
      if (!form.reportValidity()) return;
      const data = new FormData(form);
      const type = String(data.get('type'));
      const event = {
        type,
        team: teamLocked ? defaultTeam : String(data.get('team') || defaultTeam),
        half: String(data.get('half') || 'FIRST'),
        minute: Number(data.get('minute'))
      };
      if (type === 'SUBSTITUTION') {
        event.inId = Number(data.get('inId')) || null;
        event.outId = Number(data.get('outId')) || null;
      } else {
        event.playerId = canUseBothTeams ? Number(data.get('playerId')) : user.id;
        if (type === 'GOAL') {
          event.assistId = Number(data.get('assistId')) || null;
          event.penalty = data.get('penalty') === 'on';
        }
      }
      if (existing && !canEditEvent(match, existing, currentUser())) {
        toast('没有权限修改该事件或已超过可编辑时间');
        return;
      }
      const result = existing
        ? updateEvent(match, existing.id, event, currentUser().name)
        : addEvent(match, event, currentUser().name);
      if (!result.ok) {
        toast(result.message);
        return;
      }
      closeModal();
      toast(existing ? '事件已更新' : '事件已录入');
      if (options && typeof options.onSaved === 'function') options.onSaved();
    });
  }

  Object.assign(Y, { activeEvents, sanitizeEvent, validateEvent, addEvent, updateEvent, deleteEvent, eventEditWindowEndsAt, canEditEvent, halfLabel, eventTimeLabel, compareEvents, derivedScore, renderEventTimeline , openEventModal });
})();
