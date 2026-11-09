(function () {
  const DATA = window.YHFC_DATA;
  const STORAGE_KEY = 'yunhefc_prototype_v1';
  const clone = (value) => JSON.parse(JSON.stringify(value));
  const DEMO_SEED_BATCH = 3;
  const range = (from, to) => Array.from({ length: to - from + 1 }, (_, index) => from + index);
  const DEMO_SEED_PLAYER_IDS = range(22, 51);
  const DEMO_SEED_REGISTRATIONS = {
    m2: range(22, 41),
    m7: range(22, 31).concat(range(42, 51))
  };
  const DEMO_SEED_FULL_MATCHES = ['m9'];

  function defaultState() {
    return {
      currentUserId: 0,
      players: clone(DATA.players),
      matches: clone(DATA.matches),
      roles: clone(DATA.roles),
      permissions: clone(DATA.permissions),
      notifications: clone(DATA.notifications),
      audit: clone(DATA.audit),
      settings: clone(DATA.settings),
      invites: clone(DATA.invites || []),
      customFormations: clone(DATA.customFormations || [])
    };
  }

  function inferOurSide(homeName, awayName, type, title) {
    if (!['OFFICIAL', 'FRIENDLY'].includes(type)) return null;
    const ours = (name) => String(name || '').includes('云河FC');
    const home = String(homeName || '');
    const away = String(awayName || '');
    if (ours(home) && !ours(away)) return 'HOME';
    if (ours(away) && !ours(home)) return 'AWAY';
    const parts = String(title || '').split(/\s+(?:vs|VS)\s+/);
    if (parts.length === 2) {
      if (ours(parts[0]) && !ours(parts[1])) return 'HOME';
      if (ours(parts[1]) && !ours(parts[0])) return 'AWAY';
    }
    return null;
  }

  function seasonKeyOf(match) {
    const explicit = String(match.season || '').match(/\d{4}/);
    return explicit ? explicit[0] : String(new Date(match.start).getFullYear());
  }

  function defaultDeadlines(start) {
    const day = new Date(start);
    day.setHours(0, 0, 0, 0);
    const lock = new Date(day);
    lock.setHours(10, 0, 0, 0);
    return { signupDeadline: day.toISOString(), cancelLock: lock.toISOString() };
  }

  function assignMissingOrdinals(matches) {
    backfillMatchOrdinals(matches);
    const missing = matches
      .filter((match) => match.seasonMatchNo == null || match.totalMatchNo == null)
      .sort((a, b) => new Date(a.start) - new Date(b.start));
    let totalMax = Math.max(0, ...matches.map((match) => Number(match.totalMatchNo) || 0));
    for (const match of missing) {
      const key = seasonKeyOf(match);
      const seasonMax = Math.max(0, ...matches
        .filter((item) => item !== match && seasonKeyOf(item) === key)
        .map((item) => Number(item.seasonMatchNo) || 0));
      if (match.seasonMatchNo == null) match.seasonMatchNo = seasonMax + 1;
      if (match.totalMatchNo == null) match.totalMatchNo = totalMax + 1;
      totalMax = Math.max(totalMax, Number(match.totalMatchNo) || 0);
    }
    return matches;
  }

  function migrateState(loaded) {
    let changed = false;
    const mark = () => { changed = true; };
    const defaults = defaultState();
    ['players', 'matches', 'roles', 'permissions', 'notifications', 'audit', 'invites'].forEach((key) => {
      if (!Array.isArray(loaded[key])) {
        loaded[key] = defaults[key];
        mark();
      }
    });
    if (!loaded.settings || typeof loaded.settings !== 'object') {
      loaded.settings = clone(DATA.settings);
      mark();
    }
    if (!Array.isArray(loaded.customFormations)) {
      loaded.customFormations = clone(DATA.customFormations || []);
      mark();
    }
    loaded.settings = { ...DATA.settings, ...loaded.settings };
    const legacyPermissionCodes = {
      'match:view': 'match:view:any',
      'match:create': 'match:create:any',
      'match:edit': 'match:edit:any',
      'match:status:update': 'match:status:any',
      'lineup:view': 'lineup:view:any',
      'grouping:view': 'grouping:view:any'
    };
    loaded.permissions = clone(DATA.permissions);
    for (const role of loaded.roles) {
      role.permissions = (role.permissions || []).map((code) => legacyPermissionCodes[code] || code);
    }
    const allPermissionCodes = loaded.permissions.map((permission) => permission.code);
    const adminRole = loaded.roles.find((role) => role.code === 'ADMIN');
    if (adminRole && (loaded.permissionVersion !== 3 || allPermissionCodes.some((code) => !adminRole.permissions.includes(code)))) {
      adminRole.permissions = allPermissionCodes.slice();
      loaded.permissionVersion = 3;
      mark();
    }
    const manualWeight = Number(loaded.settings.groupingManualWeight);
    const autoWeight = Number(loaded.settings.groupingAutoWeight);
    const minAppearances = Number(loaded.settings.groupingMinAppearances);
    if (!Number.isFinite(manualWeight) || !Number.isFinite(autoWeight) || manualWeight < 0 || autoWeight < 0 || manualWeight + autoWeight <= 0) {
      loaded.settings.groupingManualWeight = 70;
      loaded.settings.groupingAutoWeight = 30;
      mark();
    }
    if (!Number.isInteger(minAppearances) || minAppearances < 0) {
      loaded.settings.groupingMinAppearances = 3;
      mark();
    }
    const appearanceWeight = Number(loaded.settings.groupingAppearanceWeight);
    const goalWeight = Number(loaded.settings.groupingGoalWeight);
    const assistWeight = Number(loaded.settings.groupingAssistWeight);
    if (![appearanceWeight, goalWeight, assistWeight].every((value) => Number.isFinite(value) && value >= 0) ||
      appearanceWeight + goalWeight + assistWeight <= 0) {
      loaded.settings.groupingAppearanceWeight = 50;
      loaded.settings.groupingGoalWeight = 25;
      loaded.settings.groupingAssistWeight = 25;
      mark();
    }

    for (const match of loaded.matches) {
      if (!match.lineups) { match.lineups = {}; mark(); }
      if (!['INTERNAL', 'TRAINING'].includes(match.type) && !['HOME', 'AWAY'].includes(match.ourSide)) {
        match.ourSide = inferOurSide(match.homeTeamName, match.awayTeamName, match.type, match.title) || 'HOME';
        mark();
      }
      if (match.waitlistCapacity == null) { match.waitlistCapacity = loaded.settings.maxWaitlist; mark(); }
      const startDay = new Date(match.start);
      if (!Number.isNaN(startDay.getTime())) {
        const { signupDeadline, cancelLock } = defaultDeadlines(startDay);
        if (match.signupDeadline !== signupDeadline) { match.signupDeadline = signupDeadline; mark(); }
        if (match.cancelLock !== cancelLock) { match.cancelLock = cancelLock; mark(); }
      }
      if (typeof match.includeGuests !== 'boolean') { match.includeGuests = true; mark(); }
      if (typeof match.manualStatus !== 'boolean') { match.manualStatus = false; mark(); }
      if (!match.events) { match.events = []; mark(); }
      for (const event of match.events) {
        if (event.deletedAt === undefined) { event.deletedAt = null; mark(); }
        if (event.createdBy === undefined) { event.createdBy = null; mark(); }
      }
      if (match.grouping) {
        if (!Array.isArray(match.grouping.A)) { match.grouping.A = []; mark(); }
        if (!Array.isArray(match.grouping.B)) { match.grouping.B = []; mark(); }
        if (!match.grouping.rosterFingerprint) {
          match.grouping.rosterFingerprint = rosterFingerprint(match.grouping.A.concat(match.grouping.B));
          mark();
        }
      }
    }
    if (loaded.demoSeedBatch !== DEMO_SEED_BATCH) {
      for (const id of DEMO_SEED_PLAYER_IDS) {
        if (loaded.players.some((player) => player.id === id)) continue;
        const seedPlayer = DATA.players.find((player) => player.id === id);
        if (seedPlayer) {
          loaded.players.push(clone(seedPlayer));
          mark();
        }
      }
      for (const [matchId, playerIds] of Object.entries(DEMO_SEED_REGISTRATIONS)) {
        const match = loaded.matches.find((item) => item.id === matchId);
        const seedMatch = DATA.matches.find((item) => item.id === matchId);
        if (!match || !seedMatch) continue;
        if (!Array.isArray(match.registrations)) { match.registrations = []; mark(); }
        for (const playerId of playerIds) {
          if (match.registrations.some((registration) => registration.playerId === playerId)) continue;
          const seedRegistration = seedMatch.registrations.find((registration) => registration.playerId === playerId);
          if (seedRegistration) {
            match.registrations.push(clone(seedRegistration));
            mark();
          }
        }
        const confirmed = match.registrations.filter((registration) => registration.status === 'CONFIRMED');
        const formalConfirmed = confirmed.filter((registration) => registration.type === 'PLAYER').length;
        const guestConfirmed = confirmed.filter((registration) => registration.type === 'GUEST').length;
        if ((Number(match.capacity) || 0) < formalConfirmed) {
          match.capacity = formalConfirmed;
          mark();
        }
        if ((Number(match.guestCapacity) || 0) < guestConfirmed) {
          match.guestCapacity = guestConfirmed;
          mark();
        }
      }
      for (const matchId of DEMO_SEED_FULL_MATCHES) {
        const seedMatch = DATA.matches.find((item) => item.id === matchId);
        if (!seedMatch) continue;
        let match = loaded.matches.find((item) => item.id === matchId);
        if (!match) {
          match = clone(seedMatch);
          loaded.matches.push(match);
          mark();
        }
        if (!Array.isArray(match.registrations)) { match.registrations = []; mark(); }
        if (match.format !== seedMatch.format) {
          match.format = seedMatch.format;
          mark();
        }
        for (const seedRegistration of seedMatch.registrations) {
          if (match.registrations.some((registration) => registration.playerId === seedRegistration.playerId)) continue;
          match.registrations.push(clone(seedRegistration));
          mark();
        }
        const confirmed = match.registrations.filter((registration) => registration.status === 'CONFIRMED');
        const formalConfirmed = confirmed.filter((registration) => registration.type === 'PLAYER').length;
        const guestConfirmed = confirmed.filter((registration) => registration.type === 'GUEST').length;
        if ((Number(match.capacity) || 0) < formalConfirmed) {
          match.capacity = formalConfirmed;
          mark();
        }
        if ((Number(match.guestCapacity) || 0) < guestConfirmed) {
          match.guestCapacity = guestConfirmed;
          mark();
        }
      }
      loaded.demoSeedBatch = DEMO_SEED_BATCH;
      mark();
    }
    loaded.matches = normalizeLineups(loaded.matches);
    for (const match of loaded.matches) {
      for (const side of ['HOME', 'AWAY']) {
        const lineup = match.lineups[side];
        if (!lineup) continue;
        if (!Array.isArray(lineup.bench)) { lineup.bench = []; mark(); }
        if (!Number.isInteger(lineup.version)) { lineup.version = 0; mark(); }
        if (lineup.updatedAt === undefined) { lineup.updatedAt = null; mark(); }
        if (lineup.updatedBy === undefined) { lineup.updatedBy = null; mark(); }
      }
    }
    const missingOrdinals = loaded.matches.filter((match) => match.seasonMatchNo == null || match.totalMatchNo == null).length;
    assignMissingOrdinals(loaded.matches);
    if (missingOrdinals) mark();
    return changed;
  }

  function loadState() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw) {
        const loaded = JSON.parse(raw);
        if (migrateState(loaded)) localStorage.setItem(STORAGE_KEY, JSON.stringify(loaded));
        return loaded;
      }
    } catch (error) {
      console.warn('Failed to read prototype state', error);
    }
    const initial = defaultState();
    migrateState(initial);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(initial));
    return initial;
  }

  function normalizeLineups(matches) {
    for (const match of matches || []) {
      if (!match.lineups) match.lineups = {};
      if (match.lineups.MAIN) {
        const side = match.ourSide === 'AWAY' ? 'AWAY' : 'HOME';
        if (!match.lineups[side]) match.lineups[side] = match.lineups.MAIN;
        delete match.lineups.MAIN;
      }
      if (match.lineups.A) {
        if (!match.lineups.HOME) match.lineups.HOME = match.lineups.A;
        delete match.lineups.A;
      }
      if (match.lineups.B) {
        if (!match.lineups.AWAY) match.lineups.AWAY = match.lineups.B;
        delete match.lineups.B;
      }
    }
    return matches;
  }

  function backfillMatchOrdinals(matches) {
    matches.forEach((match) => {
      const seed = DATA.matches.find((item) => item.id === match.id);
      if (!seed) return;
      if (match.seasonMatchNo == null && seed.seasonMatchNo != null) match.seasonMatchNo = seed.seasonMatchNo;
      if (match.totalMatchNo == null && seed.totalMatchNo != null) match.totalMatchNo = seed.totalMatchNo;
    });
    return matches;
  }

  const state = loadState();

  function save() {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  }

  function resetDemo() {
    localStorage.removeItem(STORAGE_KEY);
    location.href = 'index.html';
  }

  const DEMO_PHONES = {
    1: '13800000000',
    2: '13900000002',
    3: '13900000001',
    21: '13900000021'
  };

  function currentUser() {
    if (!state.currentUserId) {
      return { id: 0, name: '访客', type: 'VISITOR', status: 'VISITOR', admin: false, color: '#64748b' };
    }
    return state.players.find((player) => player.id === state.currentUserId) ||
      { id: 0, name: '访客', type: 'VISITOR', status: 'VISITOR', admin: false, color: '#64748b' };
  }

  function rolesOf(player) {
    if (!player || !player.id) return ['VISITOR'];
    if (Array.isArray(player.roles) && player.roles.length) return player.roles;
    if (player.admin) return ['ADMIN', 'PLAYER'];
    if (player.type === 'GUEST') return ['GUEST'];
    return ['PLAYER'];
  }

  function permissionsOf(player) {
    const user = player || currentUser();
    const codes = new Set();
    rolesOf(user).forEach((roleCode) => {
      const role = state.roles.find((item) => item.code === roleCode);
      (role ? role.permissions : []).forEach((code) => codes.add(code));
    });
    return Array.from(codes);
  }

  function hasPermission(code, player) {
    return permissionsOf(player).includes(code);
  }

  const ADMIN_SECTION_PERMISSIONS = {
    dashboard: null,
    matches: 'match:view:any',
    registrations: 'attendance:view:any',
    events: 'event:edit:any',
    lineup: 'lineup:view:any',
    grouping: 'grouping:view:any',
    players: 'player:view:any',
    roles: 'role:view:any',
    settings: 'settings:view:any',
    audit: 'audit:view:any',
    invites: 'invite:manage:any'
  };

  const ADMIN_ENTRY_PERMISSIONS = [
    'match:create:any', 'match:edit:any', 'match:status:any', 'registration:remove:any',
    'attendance:mark:any', 'event:edit:any', 'event:delete:any', 'lineup:edit:any',
    'grouping:generate:any', 'player:edit:any', 'role:manage:any', 'settings:manage:any',
    'invite:manage:any'
  ];

  function canAccessAdmin(player) {
    const user = player || currentUser();
    return user.status === 'ACTIVE' && ADMIN_ENTRY_PERMISSIONS.some((code) => hasPermission(code, user));
  }

  function roleCanManage(role) {
    return ((role && role.permissions) || []).some((code) => code.startsWith('role:manage'));
  }

  function managerRoleCodes() {
    return state.roles.filter(roleCanManage).map((role) => role.code);
  }

  function isTerminalMatch(match) {
    return Boolean(match) && ['CANCELLED', 'FINISHED'].includes(match.status);
  }

  function eventEntryOpen(match) {
    return Boolean(match) && ['IN_PROGRESS', 'FINISHED'].includes(match.status);
  }

  function isActiveManager(player, roles) {
    const managerCodes = managerRoleCodes();
    return player.status === 'ACTIVE' && (roles || rolesOf(player)).some((code) => managerCodes.includes(code));
  }

  const CANCELLED_REGISTRATION_NOTICE = '比赛已取消，报名记录仅保留、已作废，不占名额、不计考勤。';

  function registrationBadgeMeta(match, registration) {
    if (match && match.status === 'CANCELLED') return { label: '已作废', cls: 'badge-cancelled' };
    if (registration && registration.status === 'WAITLISTED') return { label: '候补', cls: 'badge-postponed' };
    if (registration && registration.status === 'CANCELLED') return { label: '已取消', cls: 'badge-muted' };
    if (registration && registration.status === 'REMOVED') return { label: '已移除', cls: 'badge-cancelled' };
    return { label: '已报名', cls: 'badge-open' };
  }

  function userRole(player) {
    const user = player || currentUser();
    if (!user.id) return 'VISITOR';
    if (user.status === 'PENDING') return 'PENDING';
    const roles = rolesOf(user);
    if (roles.includes('ADMIN') || user.admin) return 'ADMIN';
    if (roles.includes('GUEST') && !roles.includes('PLAYER')) return 'GUEST';
    if (roles.includes('PLAYER') || roles.includes('COACH')) return 'PLAYER';
    return 'GUEST';
  }

  function isVisitor() { return userRole() === 'VISITOR'; }
  function isPending() { return userRole() === 'PENDING'; }

  function setUser(id, redirect) {
    const targetUser = state.players.find((player) => player.id === Number(id));
    if (targetUser && targetUser.status === 'DISABLED') {
      toast('该账号已注销，不能登录');
      return;
    }
    state.currentUserId = Number(id) || 0;
    save();
    if (redirect) {
      const target = String(redirect);
      if (/^[A-Za-z0-9._-]+\.html(?:\?[A-Za-z0-9._=&%-]*)?$/.test(target)) {
        location.href = target;
      } else {
        location.reload();
      }
    } else {
      location.reload();
    }
  }

  function updatePlayer(playerId, patch, actorName) {
    const player = playerById(playerId);
    if (!player) return { ok: false, message: '球员不存在' };
    const self = Number(currentUser().id) === Number(playerId);
    if (!hasPermission('player:edit:any') && !(self && hasPermission('player:edit:own'))) {
      return { ok: false, message: '没有编辑球员资料的权限' };
    }
    const before = {};
    const after = {};
    if (patch.name !== undefined) {
      const name = String(patch.name).trim();
      if (!name) return { ok: false, message: '姓名不能为空' };
      if (name !== player.name) { before.name = player.name; player.name = name; after.name = name; }
    }
    if (patch.jersey !== undefined && hasPermission('player:edit:any')) {
      const jersey = Number(patch.jersey);
      if (!Number.isInteger(jersey) || jersey < 0) return { ok: false, message: '号码必须是非负整数' };
      if (jersey !== player.jersey) { before.jersey = player.jersey; player.jersey = jersey; after.jersey = jersey; }
    }
    if (patch.region !== undefined) {
      const region = String(patch.region).trim();
      if (region !== (player.region || '')) { before.region = player.region || ''; player.region = region; after.region = region; }
    }
    const positions = GROUP_POSITIONS;
    if (patch.position !== undefined) {
      if (!positions.includes(patch.position)) return { ok: false, message: '主位置无效' };
      if (patch.position !== player.position) { before.position = player.position; player.position = patch.position; after.position = patch.position; }
    }
    if (patch.secondary !== undefined) {
      const secondary = Array.from(new Set((patch.secondary || []).filter((code) => positions.includes(code) && code !== player.position)));
      const previous = Array.isArray(player.secondary) ? player.secondary : [];
      if (JSON.stringify(secondary) !== JSON.stringify(previous)) { before.secondary = previous; player.secondary = secondary; after.secondary = secondary; }
    }
    if (patch.avatar !== undefined) {
      const avatarValue = String(patch.avatar || '');
      if (avatarValue && avatarValue.length > 400000) return { ok: false, message: '头像图片过大' };
      if (avatarValue !== (player.avatar || '')) { before.avatar = player.avatar || ''; player.avatar = avatarValue; after.avatar = avatarValue ? '已更新' : '已清除'; }
    }
    if (patch.skill !== undefined && hasPermission('player:edit:any')) {
      const skill = Number(patch.skill);
      if (!Number.isInteger(skill) || skill < 1 || skill > 5) return { ok: false, message: '星级必须为 1–5' };
      if (skill !== player.skill) { before.skill = player.skill; player.skill = skill; after.skill = skill; }
    }
    player.updatedAt = new Date().toISOString();
    save();
    addAudit(actorName || currentUser().name, '编辑球员', player.name, { before, after });
    return { ok: true };
  }

  function updateProfile(playerId, patch) {
    const user = currentUser();
    if (!user.id) return { ok: false, message: '请先登录' };
    if (Number(user.id) !== Number(playerId)) return { ok: false, message: '只能编辑自己的资料' };
    if (!hasPermission('player:edit:own', user)) return { ok: false, message: '没有编辑资料的权限' };
    return updatePlayer(playerId, {
      name: patch.name,
      region: patch.region,
      position: patch.position,
      secondary: patch.secondary,
      avatar: patch.avatar
    }, user.name);
  }

  function playerById(id) { return state.players.find((player) => player.id === Number(id)); }

  function playerMap() {
    return state.players.reduce((map, player) => {
      map[player.id] = player;
      return map;
    }, {});
  }
  function matchById(id) { return state.matches.find((match) => match.id === id); }

  function paginate(list, page, pageSize) {
    const total = list.length;
    const totalPages = Math.max(1, Math.ceil(total / pageSize));
    const current = Math.min(Math.max(1, page), totalPages);
    return { items: list.slice((current - 1) * pageSize, current * pageSize), page: current, totalPages, total };
  }

  function paginationHtml(page, totalPages, total, pageSize) {
    return `<div class="pagination"${total ? '' : ' hidden'}>
      <label class="pagination-size">每页<select class="input input-sm" data-page-size>${[10, 20, 50, 100].map((size) => `<option value="${size}"${size === pageSize ? ' selected' : ''}>${size} 条</option>`).join('')}</select></label>
      <button class="btn btn-ghost btn-sm" data-page="${page - 1}"${page <= 1 ? ' disabled' : ''}>上一页</button>
      <span class="muted">第 ${page} / ${totalPages} 页 · 共 ${total} 条</span>
      <button class="btn btn-ghost btn-sm" data-page="${page + 1}"${page >= totalPages ? ' disabled' : ''}>下一页</button>
    </div>`;
  }

  function matchOrdinalLabel(match) {
    if (!match) return '';
    if (!match.seasonMatchNo && !match.totalMatchNo) return '';
    const seasonYear = match.season && match.season.match(/\d{4}/);
    const year = seasonYear ? seasonYear[0] : new Date(match.start).getFullYear();
    return `${year}赛季第${match.seasonMatchNo ?? '—'}场（累计${match.totalMatchNo ?? '—'}场）`;
  }

  function notify(userId, title, body) {
    state.notifications.unshift({
      id: 'n' + Date.now() + Math.random().toString(16).slice(2),
      userId: Number(userId),
      title,
      body,
      at: new Date().toISOString(),
      read: false
    });
    state.notifications = state.notifications.slice(0, 100);
    save();
  }

  function addAudit(actor, action, target, meta, options) {
    state.audit.unshift({
      id: 'a' + Date.now() + Math.random().toString(16).slice(2),
      actor: actor || '系统',
      action,
      target,
      at: new Date().toISOString(),
      ...(meta || {})
    });
    state.audit = state.audit.slice(0, 120);
    if (!options || options.save !== false) save();
  }

  function persistCatalogsAndAudit() {
    try {
      const persisted = JSON.parse(localStorage.getItem(STORAGE_KEY) || 'null');
      const next = persisted || clone(state);
      next.customFormations = clone(state.customFormations || []);
      next.audit = clone(state.audit || []);
      localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
    } catch (error) {
      save();
    }
  }

  function formatDate(iso) {
    const date = new Date(iso);
    return `${date.getMonth() + 1}月${date.getDate()}日`;
  }

  function formatTime(iso) {
    const date = new Date(iso);
    return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
  }

  function weekday(iso) {
    return ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][new Date(iso).getDay()];
  }

  function formatDateTime(iso) {
    return `${formatDate(iso)} ${weekday(iso)} ${formatTime(iso)}`;
  }

  function formatRelative(iso) {
    const diff = new Date(iso).getTime() - Date.now();
    const days = Math.round(diff / 86400000);
    if (Math.abs(days) >= 1) return days > 0 ? `${days} 天后` : `${Math.abs(days)} 天前`;
    const hours = Math.round(diff / 3600000);
    if (Math.abs(hours) >= 1) return hours > 0 ? `${hours} 小时后` : `${Math.abs(hours)} 小时前`;
    const minutes = Math.round(diff / 60000);
    return minutes >= 0 ? `${minutes} 分钟后` : `${Math.abs(minutes)} 分钟前`;
  }

  function initials(name) {
    if (!name) return '?';
    return name.slice(0, 2);
  }

  const POSITION_META = {
    GOALKEEPER: { label: '门将', short: 'GK', order: 3 },
    DEFENDER: { label: '后卫', short: 'DEF', order: 2 },
    MIDFIELDER: { label: '中场', short: 'MID', order: 1 },
    FORWARD: { label: '前锋', short: 'FWD', order: 0 }
  };
  const POSITIONS = Object.keys(POSITION_META).sort((a, b) => POSITION_META[a].order - POSITION_META[b].order);
  const OUTFIELD_POSITIONS = POSITIONS.filter((code) => code !== 'GOALKEEPER');
  const GROUP_POSITIONS = POSITIONS.slice().sort((a, b) => POSITION_META[b].order - POSITION_META[a].order);
  const MATCH_TYPE_META = {
    OFFICIAL: { label: '正式比赛', internal: false },
    INTERNAL: { label: '队内赛', internal: true },
    TRAINING: { label: '训练赛', internal: true },
    FRIENDLY: { label: '友谊赛', internal: false }
  };
  const MATCH_TYPES = Object.keys(MATCH_TYPE_META);
  const INTERNAL_TYPES = MATCH_TYPES.filter((type) => MATCH_TYPE_META[type].internal);
  const MATCH_STATUS_META = {
    SIGNUP_OPEN: { label: '报名中', cls: 'badge-open', rank: 0 },
    SIGNUP_CLOSED: { label: '报名截止', cls: 'badge-closed', rank: 1 },
    LOCKED: { label: '锁定', cls: 'badge-locked', rank: 2 },
    IN_PROGRESS: { label: '进行中', cls: 'badge-live', rank: 3 },
    FINISHED: { label: '已结束', cls: 'badge-finished', rank: 4 },
    POSTPONED: { label: '已延期', cls: 'badge-postponed', rank: null },
    CANCELLED: { label: '已取消', cls: 'badge-cancelled', rank: null }
  };
  const MATCH_STATUSES = Object.keys(MATCH_STATUS_META);
  const MATCH_MANUAL_STATUSES = MATCH_STATUSES.filter((status) => status !== 'POSTPONED');
  const STATUS_RANK = MATCH_STATUSES.reduce((map, code) => {
    if (MATCH_STATUS_META[code].rank != null) map[code] = MATCH_STATUS_META[code].rank;
    return map;
  }, {});
  const EVENT_TYPE_META = {
    GOAL: { label: '进球' },
    OWN_GOAL: { label: '乌龙球' },
    SUBSTITUTION: { label: '换人' },
    YELLOW_CARD: { label: '黄牌' },
    RED_CARD: { label: '红牌' }
  };
  const EVENT_TYPES = Object.keys(EVENT_TYPE_META);
  const ATTENDANCE_STATUS_META = {
    PRESENT: { label: '正常', cls: 'badge-open' },
    ABSENT: { label: '缺勤', cls: 'badge-cancelled' },
    TEMP_PRESENT: { label: '临时到场', cls: 'badge-postponed' }
  };
  const ATTENDANCE_STATUSES = Object.keys(ATTENDANCE_STATUS_META);

  function positionLabel(code) {
    return POSITION_META[code] ? POSITION_META[code].label : (code || '—');
  }

  function positionShort(code) {
    return POSITION_META[code] ? POSITION_META[code].short : '—';
  }

  function positionRank(code) {
    return POSITION_META[code] ? POSITION_META[code].order : 9;
  }

  function typeLabel(type) {
    return MATCH_TYPE_META[type] ? MATCH_TYPE_META[type].label : type;
  }

  function eventTypeLabel(type) {
    return EVENT_TYPE_META[type] ? EVENT_TYPE_META[type].label : type;
  }

  function statusLabel(status) {
    return MATCH_STATUS_META[status] ? MATCH_STATUS_META[status].label : status;
  }

  function statusClass(status) {
    return MATCH_STATUS_META[status] ? MATCH_STATUS_META[status].cls : 'badge-muted';
  }

  function roleLabel(role) {
    return {
      VISITOR: '访客',
      PENDING: '待审核',
      GUEST: '外挂球员',
      PLAYER: '正式球员',
      ADMIN: '管理员',
      COACH: '教练'
    }[role] || role;
  }

  function teamNames(match) {
    if (match.type === 'INTERNAL') {
      return { home: match.homeTeamName || '橙队', away: match.awayTeamName || '兰队' };
    }
    if (match.type === 'TRAINING') {
      return { home: match.homeTeamName || '云河FC', away: match.awayTeamName || '训练组' };
    }
    if (match.homeTeamName || match.awayTeamName) {
      return {
        home: match.homeTeamName || '主队',
        away: match.awayTeamName || '客队'
      };
    }
    if (!match.opponent) {
      return { home: '云河FC', away: match.ourSide === 'AWAY' ? '待定' : '对手' };
    }
    return match.ourSide === 'AWAY'
      ? { home: match.opponent, away: '云河FC' }
      : { home: '云河FC', away: match.opponent };
  }

  function winnerName(match) {
    if (match.status !== 'FINISHED' || match.score.home == null || match.score.away == null) return null;
    if (match.score.home > match.score.away) return teamNames(match).home;
    if (match.score.home < match.score.away) return teamNames(match).away;
    return null;
  }

  function resultOf(match) {
    if (match.status !== 'FINISHED' || match.score.home == null || match.score.away == null) {
      return { label: '—', cls: '' };
    }
    if (match.type === 'INTERNAL') {
      const names = teamNames(match);
      if (match.score.home > match.score.away) return { label: `${names.home} 胜`, cls: 'result-win' };
      if (match.score.home < match.score.away) return { label: `${names.away} 胜`, cls: 'result-win' };
      return { label: '平', cls: 'result-draw' };
    }
    const ourScore = match.ourSide === 'AWAY' ? match.score.away : match.score.home;
    const otherScore = match.ourSide === 'AWAY' ? match.score.home : match.score.away;
    if (ourScore > otherScore) return { label: '胜', cls: 'result-win' };
    if (ourScore < otherScore) return { label: '负', cls: 'result-loss' };
    return { label: '平', cls: 'result-draw' };
  }

  function scoreText(match) {
    if (match.score.home == null || match.score.away == null) return 'VS';
    return `${match.score.home} - ${match.score.away}`;
  }

  function avatar(player, size) {
    const name = player ? player.name : '访客';
    const color = player && player.color ? player.color : '#64748b';
    if (player && player.avatar) {
      return `<span class="avatar avatar-${size || 'md'}"><img src="${escapeHtml(player.avatar)}" alt="${escapeHtml(name)}"></span>`;
    }
    return `<span class="avatar avatar-${size || 'md'}" style="background:${color}">${escapeHtml(initials(name))}</span>`;
  }

  function readAvatarFile(file, maxSize) {
    return new Promise((resolve, reject) => {
      if (!file || !String(file.type || '').startsWith('image/')) {
        reject(new Error('请选择图片文件'));
        return;
      }
      if (file.size > 2 * 1024 * 1024) {
        reject(new Error('图片不能超过 2MB'));
        return;
      }
      const limit = maxSize || 256;
      const reader = new FileReader();
      reader.onerror = () => reject(new Error('图片读取失败'));
      reader.onload = () => {
        const image = new Image();
        image.onerror = () => reject(new Error('图片格式不支持'));
        image.onload = () => {
          const ratio = Math.min(1, limit / Math.max(image.width, image.height));
          const canvas = document.createElement('canvas');
          canvas.width = Math.max(1, Math.round(image.width * ratio));
          canvas.height = Math.max(1, Math.round(image.height * ratio));
          canvas.getContext('2d').drawImage(image, 0, 0, canvas.width, canvas.height);
          resolve(canvas.toDataURL('image/jpeg', 0.82));
        };
        image.src = String(reader.result);
      };
      reader.readAsDataURL(file);
    });
  }

  function stars(skill) {
    const value = Math.max(0, Math.min(5, Number(skill) || 0));
    return `<span class="stars">${'★'.repeat(value)}${'☆'.repeat(5 - value)}</span>`;
  }

  function maskPhone(phone) {
    if (!phone) return '—';
    return phone.replace(/^(\d{3})\d{4}(\d{4})$/, '$1****$2');
  }

  function escapeHtml(value) {
    return String(value == null ? '' : value)
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#39;');
  }

  function toast(message, type) {
    let root = document.getElementById('toast-root');
    if (!root) {
      root = document.createElement('div');
      root.id = 'toast-root';
      document.body.appendChild(root);
    }
    const item = document.createElement('div');
    item.className = `toast toast-${type || 'info'}`;
    item.textContent = message;
    root.appendChild(item);
    setTimeout(() => item.classList.add('toast-out'), 2300);
    setTimeout(() => item.remove(), 2800);
  }

  function openModal(title, body, footer) {
    let root = document.getElementById('modal-root');
    if (!root) {
      root = document.createElement('div');
      root.id = 'modal-root';
      document.body.appendChild(root);
    }
    root.innerHTML = `<div class="modal-backdrop" data-close-modal>
      <div class="modal" role="dialog" aria-modal="true">
        <div class="modal-head">
          <h3>${escapeHtml(title)}</h3>
          <button class="icon-btn" data-close-modal aria-label="关闭">×</button>
        </div>
        <div class="modal-body">${body}</div>
        ${footer ? `<div class="modal-foot">${footer}</div>` : ''}
      </div>
    </div>`;
    root.querySelectorAll('[data-close-modal]').forEach((element) => {
      element.addEventListener('click', (event) => {
        if (event.currentTarget === event.target) closeModal();
      });
    });
    const modal = root.querySelector('.modal');
    modal.addEventListener('click', (event) => event.stopPropagation());
    return modal;
  }

  function closeModal() {
    const root = document.getElementById('modal-root');
    if (root) root.innerHTML = '';
  }

  function renderHeader(active) {
    const host = document.getElementById('site-header');
    if (!host) return;
    const user = currentUser();
    const role = userRole(user);
    const loggedIn = Boolean(user.id);
    host.innerHTML = `<header class="site-header">
      <div class="container header-inner">
        <a class="brand" href="index.html">
          <span class="brand-mark"><img src="assets/logo.png" alt="云河FC"></span>
          <span class="brand-text">云河FC</span>
        </a>
        <nav class="nav">
          <a class="${active === 'home' ? 'active' : ''}" href="index.html">首页</a>
          <a class="${active === 'matches' ? 'active' : ''}" href="matches.html">赛程</a>
          <a class="${active === 'team' ? 'active' : ''}" href="team.html">球队</a>
          ${canAccessAdmin(user) ? `<a class="${active === 'admin' ? 'active' : ''}" href="admin.html">管理后台</a>` : ''}
        </nav>
        <div class="header-actions">
          <button class="btn btn-ghost btn-sm" data-proto-toggle>演示身份</button>
          ${loggedIn
            ? `<a class="user-chip" href="profile.html">${avatar(user, 'sm')}<span>${escapeHtml(user.name)}</span><small>${roleLabel(role)}</small></a>`
            : `<a class="btn btn-primary btn-sm" href="login.html">登录</a>`}
        </div>
      </div>
    </header>`;
  }

  function renderProtoPanel() {
    if (document.getElementById('proto-panel')) return;
    const panel = document.createElement('div');
    panel.id = 'proto-panel';
    panel.className = 'proto-panel';
    const current = state.currentUserId;
    panel.innerHTML = `<div class="proto-head">
        <div><strong>演示身份</strong><small>切换后刷新页面，操作数据保留</small></div>
        <button class="icon-btn" data-proto-close aria-label="关闭">×</button>
      </div>
      <div class="proto-list">
        ${DATA.demoUsers.map((user) => `<button class="proto-user ${Number(current) === user.id ? 'active' : ''}" data-user-id="${user.id}">
          <span>${escapeHtml(user.label)}</span><small>${escapeHtml(user.name)}</small>
        </button>`).join('')}
      </div>
      <button class="btn btn-ghost btn-block" data-reset-demo>重置演示数据</button>
      <p class="proto-note">纯前端原型 · 数据仅保存在浏览器</p>`;
    document.body.appendChild(panel);

    document.addEventListener('click', (event) => {
      const toggle = event.target.closest('[data-proto-toggle]');
      if (toggle) {
        panel.classList.toggle('open');
        return;
      }
      const userButton = event.target.closest('[data-user-id]');
      if (userButton) {
        setUser(userButton.dataset.userId);
        return;
      }
      if (event.target.closest('[data-reset-demo]')) {
        resetDemo();
        return;
      }
      if (event.target.closest('[data-proto-close]')) {
        panel.classList.remove('open');
        return;
      }
      if (panel.classList.contains('open') && !event.target.closest('#proto-panel')) {
        panel.classList.remove('open');
      }
    });
  }

  function renderMatchCard(match) {
    const names = teamNames(match);
    const winner = winnerName(match);
    const result = resultOf(match);
    return `<a class="match-card" href="match.html?id=${match.id}">
      <div class="match-card-top">
        <span class="muted">${formatDateTime(match.start)}</span>
        <span class="match-top-right">
          ${result.label !== '—' ? `<span class="result ${result.cls}">${result.label}</span>` : ''}
          ${matchOrdinalLabel(match) ? `<span class="match-ordinal">${matchOrdinalLabel(match)}</span>` : ''}
        </span>
      </div>
      <div class="match-card-teams">
        <span class="team-name ${winner === names.home ? 'winner' : ''}">${escapeHtml(names.home)}</span>
        <span class="match-score">${scoreText(match)}</span>
        <span class="team-name ${winner === names.away ? 'winner' : ''}">${escapeHtml(names.away)}</span>
      </div>
      <div class="match-card-foot">
        <span class="badge badge-type">${typeLabel(match.type)}</span>
        <span class="muted match-venue">${escapeHtml(match.venue || '')}${match.round ? ` · 第 ${match.round} 轮` : ''}</span>
        <span class="match-status ${statusClass(match.status)}">${statusLabel(match.status)}</span>
      </div>
    </a>`;
  }

  function rosterFingerprint(ids) {
    return Array.from(new Set((ids || []).map(Number).filter(Number.isFinite)))
      .sort((a, b) => a - b)
      .join(',');
  }

  function initialize() {
    const page = document.body.dataset.page || '';
    const api = window.YHFC;
    const transitioned = api.autoTransitionMatches ? api.autoTransitionMatches(state.matches) : 0;
    if (api.autoMarkAbsences && (api.autoMarkAbsences(state.matches) || transitioned)) save();
    renderHeader(page);
    renderProtoPanel();
  }

  document.addEventListener('DOMContentLoaded', initialize);

  window.YHFC = {
    state,
    DATA,
    DEMO_PHONES,
    save,
    clone,
    STORAGE_KEY,
    currentUser,
    rolesOf,
    permissionsOf,
    hasPermission,
    canAccessAdmin,
    isTerminalMatch,
    eventEntryOpen,
    isActiveManager,
    CANCELLED_REGISTRATION_NOTICE,
    registrationBadgeMeta,
    ADMIN_SECTION_PERMISSIONS,
    userRole,
    isVisitor,
    isPending,
    setUser,
    playerById,
    playerMap,
    matchById,
    paginate,
    paginationHtml,
    inferOurSide,
    seasonKeyOf,
    defaultDeadlines,
    updatePlayer,
    updateProfile,
    matchOrdinalLabel,
    notify,
    addAudit,
    persistCatalogsAndAudit,
    formatDate,
    formatTime,
    formatDateTime,
    formatRelative,
    positionLabel,
    positionShort,
    positionRank,
    typeLabel,
    POSITION_META,
    POSITIONS,
    OUTFIELD_POSITIONS,
    GROUP_POSITIONS,
    MATCH_TYPE_META,
    MATCH_TYPES,
    INTERNAL_TYPES,
    MATCH_STATUS_META,
    MATCH_STATUSES,
    MATCH_MANUAL_STATUSES,
    EVENT_TYPE_META,
    EVENT_TYPES,
    ATTENDANCE_STATUS_META,
    ATTENDANCE_STATUSES,
    eventTypeLabel,
    statusLabel,
    statusClass,
    STATUS_RANK,
    roleLabel,
    teamNames,
    winnerName,
    resultOf,
    scoreText,
    avatar,
    readAvatarFile,
    stars,
    maskPhone,
    escapeHtml,
    toast,
    openModal,
    closeModal,
    renderMatchCard,
    rosterFingerprint,
    renderProtoPanel
  };
})();
