(function () {
  const Y = window.YHFC;
  const $ = (selector, root) => (root || document).querySelector(selector);
  const $$ = (selector, root) => Array.from((root || document).querySelectorAll(selector));
  const esc = Y.escapeHtml;

  document.addEventListener('DOMContentLoaded', () => {
    if (document.body.dataset.page !== 'admin') return;
    if (!Y.canAccessAdmin()) {
      renderGate();
      return;
    }
    initAdmin();
  });

  function renderGate() {
    const app = $('#admin-app');
    app.innerHTML = `<div class="gate"><div class="card">
      <span class="brand-mark"><img src="assets/logo.png" alt="云河FC"></span>
      <h1>管理后台</h1>
      <p>这里需要管理员身份。原型里可以直接切换到管理员体验。</p>
      <button class="btn btn-primary" data-become-admin>以管理员身份进入</button>
      <p class="small" style="margin-top:14px"><a class="link" href="index.html">返回前台</a></p>
    </div></div>`;
    $('[data-become-admin]', app).addEventListener('click', () => Y.setUser(1));
  }

  function initAdmin() {
    const app = $('#admin-app');
    const params = new URLSearchParams(location.search);
    const firstMatch = Y.state.matches[0];
    let selectedMatchId = params.get('match') || (Y.state.matches.find((match) => match.status === 'SIGNUP_OPEN') || firstMatch || {}).id || '';
    let matchSelectionInitialized = Boolean(params.get('match'));
    let lineupSide = ['HOME', 'AWAY'].includes(params.get('side')) ? params.get('side') : null;
    let groupingMatchId = params.get('match') || (Y.state.matches.find((match) => Y.INTERNAL_TYPES.includes(match.type)) || firstMatch || {}).id || '';
    let selectedRoleCode = 'PLAYER';
    let selectedUserId = '3';
    let selectedBenchPlayerId = null;
    let playerFilters = { name: '', region: '', type: 'ALL', admin: 'ALL', status: 'ALL', position: 'ALL', skill: 'ALL' };
    let playerSort = 'default';
    let playerPage = 1;
    let playerPageSize = 10;
    let matchPage = 1;
    let matchPageSize = 10;
    let auditPage = 1;
    let auditPageSize = 10;
    let auditFilters = { actor: '', action: '', target: '' };

    const navItems = [
      ['matches', '⚽', '比赛管理'],
      ['registrations', '✅', '报名 / 考勤'],
      ['events', '📋', '事件 / 比分'],
      ['lineup', '🧩', '阵容'],
      ['grouping', '⚖️', '队内分组'],
      ['players', '👥', '球员'],
      ['roles', '🔐', '角色权限'],
      ['invites', '✉️', '邀请管理'],
      ['settings', '⚙️', '系统设置'],
      ['audit', '🧾', '操作日志']
    ];
    const visibleNav = navItems.filter(([key]) => {
      const permission = Y.ADMIN_SECTION_PERMISSIONS[key];
      return !permission || Y.hasPermission(permission);
    });
    const sections = visibleNav.map(([key]) => key);
    let section = sections.includes(params.get('section')) ? params.get('section') : sections[0];

    function matchPicker(selectedId, attr) {
      return `<select class="input input-sm" ${attr}>${Y.matchesByStartDesc()
        .map((match) => `<option value="${match.id}" ${match.id === selectedId ? 'selected' : ''}>${esc(match.title)} · ${Y.formatDate(match.start)}</option>`)
        .join('')}</select>`;
    }

    function render() {
      renderShell();
      renderContent();
    }

    function renderShell() {
      app.innerHTML = `<div class="admin-shell">
        <aside class="admin-sidebar">
          <div class="admin-brand"><img class="admin-brand-logo" src="assets/logo.png" alt="云河FC"><div><strong>云河FC</strong><span>TEAM CONSOLE</span></div></div>
          <nav class="admin-nav">${visibleNav.map(([key, icon, label]) => `<button data-nav="${key}" class="${section === key ? 'active' : ''}"><span>${icon}</span>${label}</button>`).join('')}</nav>
          <div class="admin-side-foot"><a class="btn btn-ghost btn-sm" href="index.html">返回前台</a></div>
        </aside>
        <div class="admin-main">
          <header class="admin-topbar">
            <div><span class="badge badge-admin">${Y.roleLabel(Y.userRole())}</span></div>
            <button class="btn btn-ghost btn-sm" data-proto-toggle>切换身份</button>
          </header>
          <main class="admin-content" id="admin-content"></main>
        </div>
      </div>`;
      $$('[data-nav]', app).forEach((button) => {
        button.addEventListener('click', () => {
          section = button.dataset.nav;
          render();
        });
      });
    }

    function renderContent() {
      const host = $('#admin-content');
      if (!host) return;
      if (!sections.includes(section)) section = sections[0];
      try {
        if (section === 'matches') renderMatches(host);
        if (section === 'registrations') renderRegistrations(host);
        if (section === 'events') renderEvents(host);
        if (section === 'lineup') renderLineup(host);
        if (section === 'grouping') renderGrouping(host);
        if (section === 'players') renderPlayers(host);
        if (section === 'roles') renderRoles(host);
        if (section === 'invites') renderInvites(host);
        if (section === 'settings') renderSettings(host);
        if (section === 'audit') renderAudit(host);
      } catch (error) {
        console.error(error);
        host.innerHTML = '<div class="empty-state card"><h2>页面加载失败</h2><p>请刷新重试；如果仍然失败，可点击“重置演示数据”。</p></div>';
      }
    }

    function bindGo(host) {
      $$('[data-go]', host).forEach((button) => {
        button.addEventListener('click', () => {
          section = button.dataset.go;
          if (button.dataset.matchId) {
            selectedMatchId = button.dataset.matchId;
            if (section === 'grouping') groupingMatchId = button.dataset.matchId;
          }
          render();
        });
      });
    }

    /* ---------------- Matches ---------------- */
    function renderMatches(host) {
      const list = Y.matchesByStartDesc();
      const paged = Y.paginate(list, matchPage, matchPageSize);
      matchPage = paged.page;
      const canEdit = Y.hasPermission('match:edit:any');
      const canStatus = Y.hasPermission('match:status:any');
      host.innerHTML = `<div class="admin-section-head">
        <div><h1>比赛管理</h1><p class="muted">创建比赛、配置名额、变更状态。</p></div>
        ${Y.hasPermission('match:create:any') ? '<button class="btn btn-primary" data-action="create-match">+ 创建比赛</button>' : ''}
      </div>
      <div class="table-wrap"><table class="data-table">
        <thead><tr><th>比赛</th><th>类型</th><th>时间</th><th>状态</th><th>报名</th><th>操作</th></tr></thead>
        <tbody>${paged.items.map((match) => {
          const statusControl = !canStatus
            ? `<span class="badge">${Y.statusLabel(match.status)}</span>`
            : `<select class="input input-sm" data-match-status="${match.id}">${Y.statusOptions(match).map((option) => `<option value="${option.status}" ${match.status === option.status ? 'selected' : ''} ${option.disabled ? `disabled title="${option.reason}"` : ''}>${Y.statusLabel(option.status)}</option>`).join('')}</select>${match.manualStatus ? ' <small class="muted">手动</small>' : ''}`;
          return `<tr>
          <td><div class="admin-match-title"><strong>${esc(match.title)}</strong><small>${esc(match.venue)}</small><small>${Y.matchOrdinalLabel(match)}</small></div></td>
          <td><span class="badge badge-type">${Y.typeLabel(match.type)}</span></td>
          <td>${Y.formatDateTime(match.start)}</td>
          <td>${statusControl}</td>
          <td>${match.status === 'CANCELLED'
            ? `${Y.confirmed(match).length} 条（已作废）`
            : `${Y.confirmed(match).length}/${match.capacity + match.guestCapacity}${Y.waitlisted(match, 'PLAYER').length ? ` <small class="muted">候补 ${Y.waitlisted(match, 'PLAYER').length}/${match.waitlistCapacity ?? Y.state.settings.maxWaitlist}</small>` : ''}`}</td>
          <td><div class="table-actions">
            <a class="btn btn-ghost btn-sm" href="match.html?id=${match.id}">查看</a>
            ${canEdit && !Y.isTerminalMatch(match) && match.status !== 'POSTPONED' ? `<button class="btn btn-ghost btn-sm" data-match-edit="${match.id}">编辑</button>` : ''}
            ${canEdit && match.status === 'POSTPONED' ? `<button class="btn btn-ghost btn-sm" data-match-reschedule="${match.id}">重新排期</button>` : ''}
            ${canEdit && !Y.isTerminalMatch(match) && match.status !== 'POSTPONED' ? `<button class="btn btn-ghost btn-sm" data-match-postpone="${match.id}">延期</button>` : ''}
            ${canStatus && !Y.isTerminalMatch(match) ? `<button class="btn btn-ghost btn-sm" data-match-cancel="${match.id}">取消</button>` : ''}
            ${canStatus && match.manualStatus && !Y.isTerminalMatch(match) && match.status !== 'POSTPONED' ? `<button class="btn btn-ghost btn-sm" data-match-auto="${match.id}">恢复自动</button>` : ''}
            <button class="btn btn-ghost btn-sm" data-go="registrations" data-match-id="${match.id}">报名</button>
            <button class="btn btn-ghost btn-sm" data-go="lineup" data-match-id="${match.id}">阵容</button>
          </div></td>
        </tr>`;
        }).join('')}</tbody>
      </table></div>
      ${Y.paginationHtml(paged.page, paged.totalPages, paged.total, matchPageSize)}`;
      $$('[data-page]', host).forEach((button) => {
        button.addEventListener('click', () => {
          matchPage = Number(button.dataset.page);
          render();
        });
      });
      $('[data-page-size]', host).addEventListener('change', (event) => {
        matchPageSize = Number(event.target.value);
        matchPage = 1;
        render();
      });
      const createMatchButton = $('[data-action="create-match"]', host);
      if (createMatchButton) createMatchButton.addEventListener('click', openCreateMatch);
      $$('[data-match-status]', host).forEach((select) => {
        select.addEventListener('change', () => {
          const match = Y.matchById(select.dataset.matchStatus);
          const nextStatus = select.value;
          select.value = match.status;
          if (nextStatus === 'POSTPONED') {
            openPostponeModal(match, false);
            return;
          }
          if (nextStatus === 'CANCELLED') {
            openCancelModal(match);
            return;
          }
          const result = Y.setMatchStatus(match, nextStatus, Y.currentUser().name, { manual: true });
          Y.toast(result.message || '比赛状态已更新');
          render();
        });
      });
      $$('[data-match-edit]', host).forEach((button) => {
        button.addEventListener('click', () => openMatchModal(Y.matchById(button.dataset.matchEdit)));
      });
      $$('[data-match-postpone]', host).forEach((button) => {
        button.addEventListener('click', () => openPostponeModal(Y.matchById(button.dataset.matchPostpone), false));
      });
      $$('[data-match-reschedule]', host).forEach((button) => {
        button.addEventListener('click', () => openPostponeModal(Y.matchById(button.dataset.matchReschedule), true));
      });
      $$('[data-match-cancel]', host).forEach((button) => {
        button.addEventListener('click', () => openCancelModal(Y.matchById(button.dataset.matchCancel)));
      });
      $$('[data-match-auto]', host).forEach((button) => {
        button.addEventListener('click', () => {
          const match = Y.matchById(button.dataset.matchAuto);
          const result = Y.setMatchStatus(match, match.status, Y.currentUser().name, { manual: false });
          Y.toast(result.ok ? '已恢复自动流转' : result.message);
          render();
        });
      });
      bindGo(host);
    }

    function localInput(date) {
      const pad = (value) => String(value).padStart(2, '0');
      return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
    }

    function dateTimeField(label, name, value, locked) {
      return `<label>${label}<span class="dt-field" data-dt-field><input type="hidden" name="${name}" value="${value}"><input class="input" type="text" value="${value.replace('T', ' ')}" readonly${locked ? '' : ' data-dt-open'} data-dt-display>${locked ? '' : '<button class="dt-trigger" type="button" data-dt-open aria-label="选择时间">📅</button>'}</span></label>`;
    }

    let dtPopover = null;

    function closeDtPopover() {
      if (!dtPopover) return;
      dtPopover.remove();
      dtPopover = null;
    }

    function openDtPopover(field) {
      closeDtPopover();
      const hidden = $('input[type="hidden"]', field);
      const display = $('[data-dt-display]', field);
      const initial = hidden.value ? new Date(hidden.value) : new Date();
      const pad = (value) => String(value).padStart(2, '0');
      let selected = hidden.value ? new Date(initial) : null;
      let viewYear = initial.getFullYear();
      let viewMonth = initial.getMonth();
      let hour = initial.getHours();
      let minute = initial.getMinutes();
      const root = document.getElementById('modal-root') || document.body;
      const pop = document.createElement('div');
      pop.className = 'dt-popover';
      root.appendChild(pop);
      pop.addEventListener('click', (event) => event.stopPropagation());
      dtPopover = pop;

      const sameDay = (a, b) => a && b && a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
      const timeOptions = (count, current) => Array.from({ length: count }, (_, index) => `<option value="${index}" ${index === current ? 'selected' : ''}>${pad(index)}</option>`).join('');

      function paint() {
        const startOffset = new Date(viewYear, viewMonth, 1).getDay();
        const daysInMonth = new Date(viewYear, viewMonth + 1, 0).getDate();
        const today = new Date();
        const cells = [];
        for (let index = 0; index < startOffset; index += 1) cells.push('<span class="dt-day empty"></span>');
        for (let day = 1; day <= daysInMonth; day += 1) {
          const current = new Date(viewYear, viewMonth, day);
          cells.push(`<button type="button" class="dt-day ${sameDay(selected, current) ? 'active' : ''} ${sameDay(today, current) ? 'today' : ''}" data-dt-day="${day}">${day}</button>`);
        }
        pop.innerHTML = `<div class="dt-head">
            <button type="button" class="dt-nav" data-dt-prev>‹</button>
            <strong>${viewYear}年${viewMonth + 1}月</strong>
            <button type="button" class="dt-nav" data-dt-next>›</button>
          </div>
          <div class="dt-week">${['日', '一', '二', '三', '四', '五', '六'].map((day) => `<span>${day}</span>`).join('')}</div>
          <div class="dt-days">${cells.join('')}</div>
          <div class="dt-time">
            <select class="input input-sm" data-dt-hour>${timeOptions(24, hour)}</select>
            <span>:</span>
            <select class="input input-sm" data-dt-minute>${timeOptions(60, minute)}</select>
          </div>
          <div class="dt-foot">
            <button type="button" class="dt-link" data-dt-clear>清除</button>
            <button type="button" class="dt-link" data-dt-today>今天</button>
            <button type="button" class="btn btn-primary btn-sm" data-dt-confirm>确定</button>
          </div>`;
        $$('[data-dt-day]', pop).forEach((button) => {
          button.addEventListener('click', () => {
            selected = new Date(viewYear, viewMonth, Number(button.dataset.dtDay));
            paint();
          });
        });
        $('[data-dt-prev]', pop).addEventListener('click', () => {
          viewMonth -= 1;
          if (viewMonth < 0) { viewMonth = 11; viewYear -= 1; }
          paint();
        });
        $('[data-dt-next]', pop).addEventListener('click', () => {
          viewMonth += 1;
          if (viewMonth > 11) { viewMonth = 0; viewYear += 1; }
          paint();
        });
        $('[data-dt-hour]', pop).addEventListener('change', (event) => { hour = Number(event.target.value); });
        $('[data-dt-minute]', pop).addEventListener('change', (event) => { minute = Number(event.target.value); });
        $('[data-dt-today]', pop).addEventListener('click', () => {
          selected = new Date();
          viewYear = selected.getFullYear();
          viewMonth = selected.getMonth();
          paint();
        });
        $('[data-dt-clear]', pop).addEventListener('click', () => {
          hidden.value = '';
          display.value = '';
          closeDtPopover();
        });
        $('[data-dt-confirm]', pop).addEventListener('click', () => {
          if (!selected) {
            Y.toast('请选择日期');
            return;
          }
          const dateValue = `${selected.getFullYear()}-${pad(selected.getMonth() + 1)}-${pad(selected.getDate())}`;
          hidden.value = `${dateValue}T${pad(hour)}:${pad(minute)}`;
          display.value = `${dateValue} ${pad(hour)}:${pad(minute)}`;
          hidden.dispatchEvent(new Event('change', { bubbles: true }));
          closeDtPopover();
          Y.toast('时间已确定');
        });
      }

      paint();
      const rect = field.getBoundingClientRect();
      const left = Math.min(Math.max(12, rect.left), window.innerWidth - pop.offsetWidth - 12);
      pop.style.left = `${left}px`;
      pop.style.top = `${rect.bottom + 6}px`;
    }

    document.addEventListener('click', (event) => {
      if (!dtPopover) return;
      if (dtPopover.contains(event.target) || (event.target.closest && event.target.closest('[data-dt-open]'))) return;
      closeDtPopover();
    });

    function openCreateMatch() {
      openMatchModal(null);
    }

    function openMatchModal(existing) {
      if (existing && Y.isTerminalMatch(existing)) {
        Y.toast('已取消或已结束的比赛不能编辑');
        return;
      }
      const settings = Y.state.settings;
      const editing = Boolean(existing);
      const start = existing ? new Date(existing.start) : new Date();
      if (!editing) {
        start.setDate(start.getDate() + 1);
        start.setHours(20, 0, 0, 0);
      }
      const end = existing ? new Date(existing.end) : new Date(start.getTime() + 2 * 60 * 60 * 1000);
      const { signupDeadline, cancelLock } = Y.defaultDeadlines(start);
      const deadline = new Date(signupDeadline);
      const lock = new Date(cancelLock);
      const type = existing ? existing.type : 'INTERNAL';
      const internal = Y.INTERNAL_TYPES.includes(type);
      const scoreHome = existing && existing.score ? (existing.score.home ?? '') : 0;
      const scoreAway = existing && existing.score ? (existing.score.away ?? '') : 0;
      const fallbackNames = existing ? Y.teamNames(existing) : null;
      const homeNameValue = existing ? (existing.homeTeamName || fallbackNames.home) : '橙队';
      const awayNameValue = existing ? (existing.awayTeamName || fallbackNames.away) : '兰队';
      const seasonValue = existing && existing.season ? existing.season : String(new Date().getFullYear());
      const roundValue = existing && existing.round ? existing.round : '';
      const sideValue = existing ? existing.ourSide : (Y.inferOurSide(homeNameValue, awayNameValue, type, existing ? existing.title : '') || 'HOME');
      const body = `<form id="match-form" class="form-grid">
        <label class="full">比赛名称<input class="input" name="title" value="${esc(existing ? existing.title : '橙队VS兰队')}" required></label>
        <label>比赛类型<select class="input" name="type"${editing ? ' disabled' : ''}>
          ${Y.MATCH_TYPES.map((value) => `<option value="${value}"${type === value ? ' selected' : ''}>${Y.typeLabel(value)}</option>`).join('')}
        </select></label>
        <label>赛制<select class="input" name="format"><option value="8人制">8 人制</option></select></label>
        ${dateTimeField('开始时间', 'start', localInput(start))}
        ${dateTimeField('结束时间', 'end', localInput(end))}
        ${dateTimeField('报名截止', 'signupDeadline', localInput(deadline), true)}
        ${dateTimeField('锁定', 'cancelLock', localInput(lock), true)}
        <label>场地<input class="input" name="venue" value="${esc(existing ? existing.venue : '聚龙足球场')}"></label>
        <label>赛事<input class="input" name="competition" value="${esc(existing ? existing.competition || '' : '')}"></label>
        <label>主队<input class="input" name="homeTeamName" value="${esc(homeNameValue)}"></label>
        <label>客队<input class="input" name="awayTeamName" value="${esc(awayNameValue)}"></label>
        <label>我方主/客<select class="input" name="ourSide"${editing || internal ? ' disabled' : ''}>
          <option value="HOME"${sideValue === 'HOME' ? ' selected' : ''}>我方主队</option>
          <option value="AWAY"${sideValue === 'AWAY' ? ' selected' : ''}>我方客队</option>
        </select></label>
        <label>赛季<input class="input" name="season" value="${esc(seasonValue)}" maxlength="4" placeholder="可选，默认当前年份"${editing ? ' disabled' : ''}></label>
        <label>轮次<input class="input" type="number" name="round" value="${roundValue}" min="1"></label>
        <label>正式名额<input class="input" type="number" name="capacity" value="${existing ? existing.capacity : settings.maxPlayers}" min="1"></label>
        <label>外挂名额<input class="input" type="number" name="guestCapacity" value="${existing ? existing.guestCapacity : (internal ? 0 : settings.maxGuests)}" min="0"></label>
        <label>候补上限<input class="input" type="number" name="waitlistCapacity" value="${existing ? existing.waitlistCapacity ?? settings.maxWaitlist : settings.maxWaitlist}" min="0"></label>
        ${editing ? '' : `<label>主队比分<input class="input" type="number" name="scoreHome" min="0" value="${scoreHome}"></label>
        <label>客队比分<input class="input" type="number" name="scoreAway" min="0" value="${scoreAway}"></label>`}
        <label class="checkline"><input type="checkbox" name="includeGuests" ${existing ? (existing.includeGuests ? 'checked' : '') : 'checked'}> 分组时包含外挂</label>
      </form>`;
      const modal = Y.openModal(editing ? '编辑比赛' : '创建比赛', body, `<button class="btn btn-primary" id="match-submit">${editing ? '保存' : '创建'}</button><button class="btn btn-ghost" data-close-modal>取消</button>`);
      modal.querySelectorAll('[data-dt-open]').forEach((trigger) => {
        trigger.addEventListener('click', () => openDtPopover(trigger.closest('[data-dt-field]')));
      });
      function setDateTime(name, date) {
        const hidden = modal.querySelector(`[data-dt-field] input[name="${name}"]`);
        if (!hidden) return;
        const display = hidden.parentElement.querySelector('[data-dt-display]');
        hidden.value = localInput(date);
        if (display) display.value = hidden.value.replace('T', ' ');
      }
      const startField = modal.querySelector('[data-dt-field] input[name="start"]');
      startField.addEventListener('change', () => {
        if (!startField.value) return;
        const startValue = new Date(startField.value);
        const { signupDeadline, cancelLock } = Y.defaultDeadlines(startValue);
        setDateTime('end', new Date(startValue.getTime() + 2 * 60 * 60 * 1000));
        setDateTime('signupDeadline', new Date(signupDeadline));
        setDateTime('cancelLock', new Date(cancelLock));
      });
      const typeField = modal.querySelector('[name="type"]');
      typeField.addEventListener('change', () => {
        const internalType = Y.INTERNAL_TYPES.includes(typeField.value);
        const guestField = modal.querySelector('[name="guestCapacity"]');
        guestField.value = internalType ? 0 : settings.maxGuests;
        const sideField = modal.querySelector('[name="ourSide"]');
        if (sideField) sideField.disabled = internalType || editing;
      });
      modal.querySelector('#match-submit').addEventListener('click', () => {
        if (editing && !Y.hasPermission('match:edit:any')) {
          Y.toast('没有编辑比赛的权限');
          return;
        }
        if (!editing && !Y.hasPermission('match:create:any')) {
          Y.toast('没有创建比赛的权限');
          return;
        }
        const form = modal.querySelector('#match-form');
        if (!form.reportValidity()) return;
        const data = new FormData(form);
        const startDate = new Date(data.get('start'));
        const endDate = new Date(data.get('end'));
        const minutesOfDay = startDate.getHours() * 60 + startDate.getMinutes();
        if (minutesOfDay <= 10 * 60) {
          Y.toast('开赛时间必须晚于当天 10:00');
          return;
        }
        if (!(startDate < endDate)) {
          Y.toast('结束时间必须晚于开始时间');
          return;
        }
        const hours = (endDate - startDate) / (60 * 60 * 1000);
        if (hours < 2 || hours > 4) {
          Y.toast('开始时间与结束时间相差必须为 2–4 小时');
          return;
        }
        const deadline = new Date(data.get('signupDeadline'));
        const lock = new Date(data.get('cancelLock'));
        const expected = Y.defaultDeadlines(startDate);
        if (deadline.getTime() !== new Date(expected.signupDeadline).getTime() || lock.getTime() !== new Date(expected.cancelLock).getTime()) {
          Y.toast('报名截止必须为开赛当天 0 点，锁定必须为当天 10 点');
          return;
        }
        const nextType = editing ? existing.type : String(data.get('type'));
        const nextInternal = Y.INTERNAL_TYPES.includes(nextType);
        const homeTeamName = String(data.get('homeTeamName') || '').trim()
          || (editing ? (existing.homeTeamName || fallbackNames.home) : '');
        const awayTeamName = String(data.get('awayTeamName') || '').trim()
          || (editing ? (existing.awayTeamName || fallbackNames.away) : '');
        const title = String(data.get('title')).trim();
        const seasonValue = editing ? existing.season : (String(data.get('season') || '').trim() || String(new Date().getFullYear()));
        if (!editing && !/^\d{4}$/.test(seasonValue)) {
          Y.toast('赛季须为四位年份');
          return;
        }
        const capacity = Number(data.get('capacity'));
        const guestCapacity = Number(data.get('guestCapacity'));
        const waitlistCapacity = Number(data.get('waitlistCapacity'));
        if (!Number.isFinite(capacity) || capacity < 1 || guestCapacity < 0 || waitlistCapacity < 0) {
          Y.toast('名额配置不合法');
          return;
        }
        if (editing) {
          const formalConfirmed = Y.confirmed(existing, 'PLAYER').length;
          const guestConfirmed = Y.confirmed(existing, 'GUEST').length;
          if (capacity < formalConfirmed) {
            Y.toast(`正式名额不能低于当前已确认人数 ${formalConfirmed}`);
            return;
          }
          if (guestCapacity < guestConfirmed) {
            Y.toast(`外挂名额不能低于当前已确认人数 ${guestConfirmed}`);
            return;
          }
        }
        const scoreHome = !editing && data.get('scoreHome') !== '' ? Math.max(0, Number(data.get('scoreHome'))) : null;
        const scoreAway = !editing && data.get('scoreAway') !== '' ? Math.max(0, Number(data.get('scoreAway'))) : null;
        const inferredSide = Y.inferOurSide(homeTeamName, awayTeamName, nextType, title);
        const selectedSide = editing ? existing.ourSide : String(data.get('ourSide') || '');
        const ourSide = nextInternal ? null : (editing ? existing.ourSide : (selectedSide || inferredSide || 'HOME'));
        const derivedOpponent = ourSide === 'AWAY' ? homeTeamName : awayTeamName;
        const opponent = nextInternal ? '' : (derivedOpponent || (editing ? existing.opponent || '' : ''));
        const payload = {
          title,
          type: nextType,
          start: startDate.toISOString(),
          end: endDate.toISOString(),
          signupDeadline: deadline.toISOString(),
          cancelLock: lock.toISOString(),
          venue: String(data.get('venue') || ''),
          competition: nextInternal ? '' : String(data.get('competition') || ''),
          season: seasonValue,
          round: nextInternal ? null : Number(data.get('round')) || null,
          capacity,
          guestCapacity,
          waitlistCapacity,
          includeGuests: data.get('includeGuests') === 'on',
          manualStatus: editing ? existing.manualStatus : false,
          homeTeamName,
          awayTeamName,
          ourSide,
          opponent,
          ...(editing ? {} : { score: { home: scoreHome, away: scoreAway } })
        };
        if (editing) {
          const before = Y.matchSnapshot(existing);
          Object.assign(existing, payload);
          Y.save();
          Y.addAudit(Y.currentUser().name, '编辑比赛', payload.title, { before, after: Y.matchSnapshot(existing) });
          if (before.start !== payload.start || before.venue !== payload.venue) {
            Y.notifyRegistrants(existing, '比赛信息变更', `「${existing.title}」时间或场地已更新。`);
          }
          Y.toast('比赛已保存');
        } else {
          const ordinals = Y.nextOrdinals(Y.state.matches, payload.season, payload.start);
          const match = {
            id: 'm' + Date.now(),
            ...payload,
            ...ordinals,
            format: '8人制',
            status: 'SIGNUP_OPEN',
            registrations: [],
            attendance: [],
            events: [],
            lineups: {},
            grouping: null
          };
          Y.state.matches.unshift(match);
          Y.save();
          Y.addAudit(Y.currentUser().name, '创建比赛', match.title);
          Y.toast('比赛已创建');
          selectedMatchId = match.id;
          section = 'matches';
        }
        Y.closeModal();
        render();
      });
    }

    function openPostponeModal(match, rescheduling) {
      const start = new Date(match.start);
      const end = new Date(match.end);
      const defaults = Y.defaultDeadlines(start);
      const body = `<form id="postpone-form" class="form-grid">
        ${dateTimeField('新开始时间', 'start', localInput(start))}
        ${dateTimeField('新结束时间', 'end', localInput(end))}
        ${dateTimeField('报名截止', 'signupDeadline', localInput(new Date(defaults.signupDeadline)), true)}
        ${dateTimeField('锁定', 'cancelLock', localInput(new Date(defaults.cancelLock)), true)}
        <p class="muted small full">报名记录保留；报名截止重算为开赛当天 0 点，锁定重算为当天 10 点；${rescheduling ? '重新排期后回报名中并恢复自动流转。' : '延期后状态为已延期，等待重新排期。'}</p>
      </form>`;
      const modal = Y.openModal(rescheduling ? '重新排期' : '延期比赛', body, `<button class="btn btn-primary" id="postpone-submit">${rescheduling ? '确认重新排期' : '确认延期'}</button><button class="btn btn-ghost" data-close-modal>取消</button>`);
      modal.querySelectorAll('[data-dt-open]').forEach((trigger) => {
        trigger.addEventListener('click', () => openDtPopover(trigger.closest('[data-dt-field]')));
      });
      function setDateTime(name, date) {
        const hidden = modal.querySelector(`[data-dt-field] input[name="${name}"]`);
        if (!hidden) return;
        hidden.value = localInput(date);
        const display = hidden.parentElement.querySelector('[data-dt-display]');
        if (display) display.value = hidden.value.replace('T', ' ');
      }
      const startField = modal.querySelector('[data-dt-field] input[name="start"]');
      startField.addEventListener('change', () => {
        if (!startField.value) return;
        const startValue = new Date(startField.value);
        const { signupDeadline, cancelLock } = Y.defaultDeadlines(startValue);
        setDateTime('end', new Date(startValue.getTime() + 2 * 60 * 60 * 1000));
        setDateTime('signupDeadline', new Date(signupDeadline));
        setDateTime('cancelLock', new Date(cancelLock));
      });
      modal.querySelector('#postpone-submit').addEventListener('click', () => {
        const form = modal.querySelector('#postpone-form');
        if (!form.reportValidity()) return;
        const data = new FormData(form);
        const startDate = new Date(data.get('start'));
        const endDate = new Date(data.get('end'));
        if (startDate.getTime() <= Date.now()) {
          Y.toast('新开始时间必须晚于当前时间');
          return;
        }
        if (startDate.getHours() * 60 + startDate.getMinutes() <= 10 * 60) {
          Y.toast('开赛时间必须晚于当天 10:00');
          return;
        }
        if (!(startDate < endDate)) {
          Y.toast('结束时间必须晚于开始时间');
          return;
        }
        const hours = (endDate - startDate) / (60 * 60 * 1000);
        if (hours < 2 || hours > 4) {
          Y.toast('开始时间与结束时间相差必须为 2–4 小时');
          return;
        }
        const cancelLock = new Date(data.get('cancelLock'));
        if (!(cancelLock < startDate)) {
          Y.toast('锁定时间必须早于开赛时间');
          return;
        }
        const changes = {
          start: startDate.toISOString(),
          end: endDate.toISOString(),
          signupDeadline: new Date(data.get('signupDeadline')).toISOString(),
          cancelLock: cancelLock.toISOString()
        };
        const result = rescheduling
          ? Y.rescheduleMatch(match, changes, Y.currentUser().name)
          : Y.postponeMatch(match, changes, Y.currentUser().name);
        if (!result.ok) {
          Y.toast(result.message);
          return;
        }
        Y.toast(rescheduling ? '比赛已重新排期' : '比赛已延期');
        Y.closeModal();
        render();
      });
    }

    function openCancelModal(match) {
      const body = `<form id="cancel-form" class="form-grid">
        <label class="full">取消原因<textarea class="input" name="reason" rows="3" required placeholder="例如：场地临时关闭"></textarea></label>
        <p class="muted small full">取消后报名记录保留但作废，不计考勤；该操作需二次确认。</p>
      </form>`;
      const modal = Y.openModal('取消比赛', body, '<button class="btn btn-danger" id="cancel-submit">确认取消</button><button class="btn btn-ghost" data-close-modal>取消</button>');
      modal.querySelector('#cancel-submit').addEventListener('click', () => {
        const form = modal.querySelector('#cancel-form');
        if (!form.reportValidity()) return;
        const reason = String(new FormData(form).get('reason') || '').trim();
        if (!reason) { Y.toast('请填写取消原因'); return; }
        if (!window.confirm(`确认取消「${match.title}」？报名记录将作废。`)) return;
        const result = Y.cancelMatch(match, reason, Y.currentUser().name);
        if (!result.ok) { Y.toast(result.message); return; }
        Y.toast('比赛已取消');
        Y.closeModal();
        render();
      });
    }

    /* ---------------- Registrations / attendance ---------------- */
    function renderRegistrations(host) {
      if (!matchSelectionInitialized) {
        const latest = Y.matchesByStartDesc()[0];
        if (latest) selectedMatchId = latest.id;
        matchSelectionInitialized = true;
      }
      const match = Y.matchById(selectedMatchId) || Y.state.matches[0];
      if (!match) {
        host.innerHTML = '<div class="empty-state card"><h2>暂无比赛</h2><p>请先创建比赛后再管理报名与考勤。</p></div>';
        return;
      }
      selectedMatchId = match.id;
      const formal = Y.confirmed(match, 'PLAYER');
      const formalWait = Y.waitlisted(match, 'PLAYER');
      const guest = Y.confirmed(match, 'GUEST');
      const waitlistCapacity = match.waitlistCapacity ?? Y.state.settings.maxWaitlist;
      host.innerHTML = `<div class="admin-section-head">
        <div><h1>报名 / 考勤</h1><p class="muted">移除已确认报名会自动递补；临时到场不占名额。</p></div>
        ${matchPicker(match.id, 'data-match-picker')}
      </div>
      <div class="stat-cards">
        <div class="stat-card"><span>正式报名</span><b>${formal.length}</b><small>${match.status === 'CANCELLED' ? '已作废' : `名额 ${match.capacity}`}</small></div>
        <div class="stat-card"><span>外挂报名</span><b>${guest.length}</b><small>${match.status === 'CANCELLED' ? '已作废' : `名额 ${match.guestCapacity}`}</small></div>
        <div class="stat-card"><span>候补（正式）</span><b>${match.status === 'CANCELLED' ? `${formalWait.length} 条` : `${formalWait.length}/${waitlistCapacity}`}</b><small>${match.status === 'CANCELLED' ? '已作废' : '全场合计'}</small></div>
        ${match.status === 'CANCELLED' ? '' : `<div class="stat-card"><span>临时到场</span><b>${(match.attendance || []).filter((item) => item.status === 'TEMP_PRESENT').length}</b><small>管理员补录</small></div>`}
      </div>
      ${match.status === 'CANCELLED' ? `<div class="banner banner-warn">${Y.CANCELLED_REGISTRATION_NOTICE}</div>` : ''}
      <div class="registration-admin-grid">
        <section class="registration-admin-column">
          <h3>正式球员</h3>
          ${formal.map((reg) => regAdminRow(match, reg, false)).join('') || '<div class="empty-inline">暂无正式球员报名</div>'}
          ${formalWait.map((reg) => regAdminRow(match, reg, true)).join('')}
        </section>
        <section class="registration-admin-column">
          <h3>外挂球员 · 不设候补</h3>
          ${guest.map((reg) => regAdminRow(match, reg, false)).join('') || '<div class="empty-inline">暂无外挂球员报名</div>'}
          ${['CANCELLED', 'POSTPONED'].includes(match.status) || !Y.hasPermission('attendance:mark:any') ? '' : '<button class="btn btn-ghost btn-sm" data-action="temp-add" style="margin-top:10px">+ 补录临时到场</button>'}
        </section>
      </div>
      <section class="admin-form-card" style="margin-top:14px"${['CANCELLED', 'POSTPONED'].includes(match.status) ? ' hidden' : ''}>
        <h3>临时到场</h3>
        ${(match.attendance || []).filter((item) => item.status === 'TEMP_PRESENT').map((item) => {
          const player = Y.playerById(item.playerId);
          return `<div class="reg-admin-row">${Y.avatar(player, 'sm')}<div class="grow"><strong>${esc(player?.name || '未知')}</strong><small>未报名 · 临时到场</small></div><span class="badge badge-postponed">临时到场</span></div>`;
        }).join('') || '<div class="empty-inline">暂无临时到场</div>'}
      </section>`;
      $('[data-match-picker]', host).addEventListener('change', (event) => {
        selectedMatchId = event.target.value;
        render();
      });
      bindAttendance(host, match);
      const tempButton = $('[data-action="temp-add"]', host);
      if (tempButton) tempButton.addEventListener('click', () => openTempAttendance(match));
    }

    function regAdminRow(match, reg, waitlist) {
      const player = Y.playerById(reg.playerId);
      const attendance = Y.attendanceOf(match, reg.playerId);
      const voided = ['CANCELLED', 'POSTPONED'].includes(match.status);
      const canMark = Y.hasPermission('attendance:mark:any');
      const canRemove = Y.hasPermission('registration:remove:any');
      const badge = Y.registrationBadgeMeta(match, reg);
      return `<div class="reg-admin-row">
        ${Y.avatar(player, 'sm')}
        <div class="grow"><strong>${esc(player?.name || '未知球员')}</strong><small>#${player?.jersey ?? '—'} · ${Y.positionLabel(player?.position)}</small></div>
        <span class="badge ${badge.cls}">${badge.label}</span>
        ${voided || waitlist || !canMark ? '' : `<div class="att-buttons">
          <button class="${attendance?.status === 'PRESENT' ? 'active' : ''}" data-att="PRESENT" data-player-id="${reg.playerId}">正常</button>
          <button class="absent ${attendance?.status === 'ABSENT' ? 'active' : ''}" data-att="ABSENT" data-player-id="${reg.playerId}">缺勤</button>
        </div>`}
        ${canRemove && !Y.isTerminalMatch(match) && match.status !== 'POSTPONED' ? `<button class="icon-btn danger" data-remove="${reg.playerId}" title="移除报名">×</button>` : ''}
      </div>`;
    }

    function bindAttendance(host, match) {
      $$('[data-att]', host).forEach((button) => {
        button.addEventListener('click', () => {
          const result = Y.markAttendance(match, Number(button.dataset.playerId), button.dataset.att, 'ADMIN', Y.currentUser().name);
          Y.toast(result.ok ? '考勤已更新' : result.message);
          if (result.ok) render();
        });
      });
      $$('[data-remove]', host).forEach((button) => {
        button.addEventListener('click', () => {
          if (!window.confirm('确认移除这名球员的报名？若为已确认报名，候补会自动递补。')) return;
          const result = Y.removeRegistration(match, Number(button.dataset.remove), Y.currentUser().name);
          Y.toast(result.ok ? (result.promoted ? '已移除，候补已递补' : '已移除') : result.message);
          if (result.ok) render();
        });
      });
    }

    function openTempAttendance(match) {
      const eligible = Y.state.players.filter((player) =>
        player.status === 'ACTIVE' && !Y.confirmed(match).some((reg) => Number(reg.playerId) === player.id)
      );
      if (!eligible.length) {
        Y.toast('没有可补录的球员');
        return;
      }
      const options = eligible.map((player) => `<option value="${player.id}">${esc(player.name)} #${player.jersey} · ${Y.positionLabel(player.position)}</option>`).join('');
      const modal = Y.openModal('补录临时到场', `<form id="temp-form" class="form-stack">
        <label>球员<select class="input" name="playerId">${options}</select></label>
        <p class="muted small">仅补录未确认报名的球员（含候补/已取消/未报名）；临时到场不占名额、不计缺勤。</p>
      </form>`, '<button class="btn btn-primary" id="temp-submit">补录</button><button class="btn btn-ghost" data-close-modal>取消</button>');
      modal.querySelector('#temp-submit').addEventListener('click', () => {
        const playerId = Number(new FormData(modal.querySelector('#temp-form')).get('playerId'));
        const result = Y.markAttendance(match, playerId, 'TEMP_PRESENT', 'ADMIN', Y.currentUser().name);
        if (!result.ok) {
          Y.toast(result.message);
          return;
        }
        Y.closeModal();
        Y.toast('已补录临时到场');
        render();
      });
    }

    /* ---------------- Events / score ---------------- */
    function renderEvents(host) {
      const match = Y.matchById(selectedMatchId) || Y.state.matches[0];
      if (!match) {
        host.innerHTML = '<div class="empty-state card"><h2>暂无比赛</h2><p>请先创建比赛后再录入事件。</p></div>';
        return;
      }
      selectedMatchId = match.id;
      const names = Y.teamNames(match);
      const events = Y.activeEvents(match).slice().sort(Y.compareEvents);
      const derived = Y.derivedScore(match);
      const readOnly = ['CANCELLED', 'POSTPONED'].includes(match.status);
      host.innerHTML = `<div class="admin-section-head">
        <div><h1>事件 / 比分</h1><p class="muted">比分与事件独立录入，不一致只提示。</p></div>
        ${matchPicker(match.id, 'data-match-picker')}
      </div>
      <div class="events-admin-grid">
        <section class="admin-form-card">
          <h3>比分</h3>
          <div class="score-editor">
            <label>${esc(names.home)}<input class="input" type="number" min="0" data-score="home" value="${match.score.home ?? 0}"${readOnly ? ' disabled' : ''}></label>
            <span>:</span>
            <label>${esc(names.away)}<input class="input" type="number" min="0" data-score="away" value="${match.score.away ?? 0}"${readOnly ? ' disabled' : ''}></label>
            <button class="btn btn-primary btn-sm" data-action="save-score"${readOnly ? ' disabled' : ''}>保存</button>
          </div>
          <p class="muted small">事件推导：${derived.ours} - ${derived.opponent}；对方进球未记录时会有差异。</p>
          ${Y.eventEntryOpen(match) ? '<button class="btn btn-ghost btn-sm" data-action="add-event">+ 录入事件</button>' : '<p class="muted small">仅进行中或已结束的比赛可录入事件。</p>'}
        </section>
        <section class="admin-form-card">
          <h3>事件时间线</h3>
          ${events.length ? Y.renderEventTimeline(match, events, { admin: !readOnly, canEdit: () => !readOnly }) : '<div class="empty-inline">暂无事件</div>'}
        </section>
      </div>`;

      $('[data-match-picker]', host).addEventListener('change', (event) => {
        selectedMatchId = event.target.value;
        render();
      });
      $('[data-action="save-score"]', host).addEventListener('click', () => {
        if (readOnly) {
          Y.toast('已取消或已延期的比赛不能修改比分');
          return;
        }
        const home = $('[data-score="home"]', host).value;
        const away = $('[data-score="away"]', host).value;
        match.score.home = home === '' ? null : Number(home);
        match.score.away = away === '' ? null : Number(away);
        Y.save();
        Y.addAudit(Y.currentUser().name, '修改比分', `${match.title} ${match.score.home ?? '-'}-${match.score.away ?? '-'}`);
        const currentDerived = Y.derivedScore(match);
        const mismatch = Number(match.score.home ?? 0) !== currentDerived.home || Number(match.score.away ?? 0) !== currentDerived.away;
        Y.toast(mismatch ? '比分已保存；与事件推导不一致' : '比分已保存');
        render();
      });
      const addEventButton = $('[data-action="add-event"]', host);
      if (addEventButton) addEventButton.addEventListener('click', () => Y.openEventModal(match, null, { onSaved: render }));
      $$('[data-delete-event]', host).forEach((button) => {
        button.addEventListener('click', () => {
          if (!window.confirm('确认删除这条事件？')) return;
          const result = Y.deleteEvent(match, button.dataset.deleteEvent, Y.currentUser().name);
          Y.toast(result.ok ? '事件已删除' : result.message);
          if (result.ok) render();
        });
      });
      $$('[data-edit-event]', host).forEach((button) => {
        button.addEventListener('click', () => {
          const event = Y.activeEvents(match).find((item) => item.id === button.dataset.editEvent);
          if (event) Y.openEventModal(match, event, { onSaved: render });
        });
      });
    }

/* ---------------- Lineup ---------------- */
    function renderLineup(host) {
      const match = Y.matchById(selectedMatchId) || Y.state.matches[0];
      if (!match) {
        host.innerHTML = '<div class="empty-state card"><h2>暂无比赛</h2><p>请先创建比赛后再排阵容。</p></div>';
        return;
      }
      if (match.id !== selectedMatchId) selectedBenchPlayerId = null;
      selectedMatchId = match.id;
      const internal = Y.INTERNAL_TYPES.includes(match.type);
      const sideNames = Y.teamNames(match);
      if (!['HOME', 'AWAY'].includes(lineupSide)) lineupSide = Y.ourLineupSide(match);
      if (!match.lineups) match.lineups = {};
      const opponentSide = Y.isOpponentLineup(match, lineupSide);
      let eligible = [];
      try {
        eligible = Y.lineupEligiblePlayers(match, lineupSide) || [];
      } catch (error) {
        eligible = [];
      }
      const registrations = Y.confirmed(match)
        .map((registration) => ({ registration, player: Y.playerById(registration.playerId) }))
        .filter((item) => item.player);
      const groupOfSide = (playerId, side) => {
        if (!internal || !match.grouping) return '';
        const ids = match.grouping[Y.groupingTeamForSide(side)] || [];
        return ids.map(Number).includes(Number(playerId)) ? side : '';
      };
      const eligibleAllIds = new Set(Y.groupingEligiblePlayers(match).map((player) => Number(player.id)));
      const ungrouped = registrations.filter(({ player }) => eligibleAllIds.has(Number(player.id)) && !groupOfSide(player.id, 'HOME') && !groupOfSide(player.id, 'AWAY'));
      const nonParticipating = registrations.filter(({ player }) => !eligibleAllIds.has(Number(player.id)) && player.status === 'ACTIVE' && player.type === 'GUEST' && !match.includeGuests);
      const lineupRosterRow = ({ registration, player }) => `<div class="reg-admin-row">${Y.avatar(player, 'sm')}<div class="grow"><strong>${esc(player.name)}</strong><small>#${player.jersey ?? '—'} · ${Y.positionShort(player.position)} · ${registration.type === 'GUEST' ? '外挂' : '球队球员'}</small></div></div>`;
      if (!match.lineups[lineupSide]) {
        match.lineups[lineupSide] = {
          formationId: '232',
          starters: {},
          bench: opponentSide ? [] : eligible.map((player) => player.id),
          version: 0
        };
      }
      const lineup = match.lineups[lineupSide];
      if (!Array.isArray(lineup.bench)) lineup.bench = [];
      if (!opponentSide) {
        const eligibleIds = new Set(eligible.map((player) => Number(player.id)));
        Object.keys(lineup.starters || {}).forEach((slot) => {
          if (lineup.starters[slot] && !eligibleIds.has(Number(lineup.starters[slot]))) delete lineup.starters[slot];
        });
        lineup.bench = lineup.bench.filter((id) => eligibleIds.has(Number(id)));
      }
      const formation = Y.currentFormation(lineup);
      const starters = lineup.starters || {};
      const starterIds = Object.values(starters).map(Number);
      const benchIds = lineup.bench.map(Number).filter((id) => eligible.some((player) => player.id === id));
      const unassigned = eligible.filter((player) => !starterIds.includes(player.id) && !benchIds.includes(player.id));
      const rosterDiff = internal && match.grouping ? Y.groupingRosterDiff(match, match.grouping) : null;
      const staleGrouping = Boolean(rosterDiff && rosterDiff.changed);
      const playerNameOf = (id) => Y.playerById(id)?.name || `#${id}`;
      const expectedVersion = Number(lineup.version) || 0;
      const benchPlayers = benchIds.map((id) => Y.playerById(id)).filter(Boolean);
      const formationOptions = Y.allFormations().map((item) => `<option value="${item.id}" ${item.id === formation.id ? 'selected' : ''}>${esc(item.name)}</option>`).join('');
      host.innerHTML = `<div class="admin-section-head">
        <div><h1>阵容</h1><p class="muted">拖拽或点击替补，再点球场位置；点击已占位置移回替补。</p></div>
        ${matchPicker(match.id, 'data-match-picker')}
      </div>
      ${staleGrouping ? `<div class="banner banner-warn">分组名单已变化：新增 ${rosterDiff.addedIds.map(playerNameOf).join('、') || '无'}；移除 ${rosterDiff.removedIds.map(playerNameOf).join('、') || '无'}。未分组球员不会出现在首发/替补，请先到 <a href="admin.html?section=grouping&match=${match.id}">队内分组</a> 重新生成并保存。</div>` : ''}
      ${!opponentSide && starterIds.filter(Boolean).length === 0 ? '<div class="banner">尚未安排首发：请从替补中拖拽 8 名球员到球场位置。</div>' : ''}
      <div class="lineup-layout">
        <section class="lineup-panel">
          <div class="formation-bar">
            <label>阵型 <select data-formation>${formationOptions}</select></label>
            <div class="chip-row">${['HOME', 'AWAY'].map((side) => `<button class="chip ${side === lineupSide ? 'active' : ''}" data-side="${side}">${side === 'HOME' ? sideNames.home : sideNames.away}</button>`).join('')}</div>
            ${opponentSide || !Y.hasPermission('lineup:edit:any') ? '' : '<button class="btn btn-ghost btn-sm" data-action="save-template">另存为模板</button>'}
            ${Y.hasPermission('lineup:edit:any') ? `<button class="btn btn-primary btn-sm" data-action="save-lineup"${Y.isTerminalMatch(match) || match.status === 'POSTPONED' ? ' disabled' : ''}>保存阵容</button>` : '<span class="muted small">只读</span>'}
          </div>
          ${Y.pitchHtml(lineup, opponentSide ? {} : Y.playerMap(), true)}
          <p class="muted small">当前 ${formation.name} · ${opponentSide ? '对手方仅记录阵型与占位槽位，不录入球员姓名' : `已排 ${Object.keys(starters).length}/8`}${!opponentSide && internal && !match.grouping ? ' · 尚未生成分组，暂用全部确认名单' : ''}${!opponentSide && internal && match.grouping ? ' · 仅显示当前分组球员' : ''}${!opponentSide ? ' · 修改需点击“保存阵容”生效' : ''}</p>
        </section>
        <section class="bench-panel">
          <h3>替补</h3>
          <div class="bench-list">${opponentSide ? '<div class="empty-inline">对手方不录入球员，仅记录阵型和占位槽位</div>' : (benchPlayers.map((player) => `<div class="bench-item ${selectedBenchPlayerId === player.id ? 'selected' : ''}" draggable="true" data-bench-player="${player.id}">${Y.avatar(player, 'xs')}<span>${esc(player.name)}</span><small>#${player.jersey} · ${Y.positionShort(player.position)}</small><button class="icon-btn" type="button" data-bench-remove="${player.id}" title="移出替补">×</button></div>`).join('') || '<div class="empty-inline">暂无替补</div>')}</div>
          ${opponentSide ? '' : `<h3 style="margin-top:14px">未分配</h3><div class="bench-list">${unassigned.map((player) => `<div class="bench-item"><span>${esc(player.name)}</span><small>#${player.jersey} · ${Y.positionShort(player.position)}</small><button class="btn btn-ghost btn-sm" type="button" data-bench-add="${player.id}">加入替补</button></div>`).join('') || '<div class="empty-inline">名单已全部分配</div>'}</div>`}
        </section>
      </div>
      <section class="card" style="margin-top:14px">
        <div class="card-head"><h2>报名球员</h2><span class="muted">${registrations.length} 人 · 按分组选择队伍</span></div>
        ${internal && match.grouping ? `<div class="group-admin-grid">${['HOME', 'AWAY'].map((side) => {
          const teamName = side === 'HOME' ? sideNames.home : sideNames.away;
          const list = registrations.filter(({ player }) => groupOfSide(player.id, side));
          return `<div class="group-column lineup-roster-column ${side === lineupSide ? 'active' : ''}" data-lineup-team="${side}"><div class="group-head"><strong>${esc(teamName)}</strong><span>${list.length} 人</span></div>${list.map(({ player }) => `<span class="player-chip">${Y.avatar(player, 'xs')}<span>${esc(player.name)}</span><small>#${player.jersey ?? '—'} · ${Y.positionShort(player.position)}</small></span>`).join('') || '<div class="empty-inline">该队暂无报名球员</div>'}</div>`;
        }).join('')}</div>${ungrouped.length ? `<div style="margin-top:12px"><h3>未分组（无法排入阵容）</h3><div class="group-roster-list">${ungrouped.map(lineupRosterRow).join('')}</div></div>` : ''}${nonParticipating.length ? `<div style="margin-top:12px"><h3>不参与（外挂）</h3><div class="group-roster-list">${nonParticipating.map(lineupRosterRow).join('')}</div></div>` : ''}
        <p class="muted small" style="margin-top:10px">点击橙队/兰队切换阵容；每队只能拖拽、移动和保存本队分组成员。</p>`
        : `<div class="group-roster-list">${registrations.map(lineupRosterRow).join('') || '<div class="empty-inline">本场暂无确认报名球员</div>'}</div>`}
      </section>`;
      $('[data-match-picker]', host).addEventListener('change', (event) => {
        selectedMatchId = event.target.value;
        selectedBenchPlayerId = null;
        const next = Y.matchById(selectedMatchId);
        lineupSide = Y.ourLineupSide(next);
        render();
      });
      const formationSelect = $('[data-formation]', host);
      formationSelect.addEventListener('change', () => {
        if (Object.keys(lineup.starters).length && !window.confirm('切换阵型会清空当前首发，确认继续？')) {
          formationSelect.value = lineup.formationId;
          return;
        }
        lineup.formationId = formationSelect.value;
        lineup.starters = {};
        lineup.bench = eligible.map((player) => player.id);
        selectedBenchPlayerId = null;
        Y.toast('阵型已切换，请重新安排首发并保存');
        render();
      });
      $$('[data-side]', host).forEach((button) => {
        button.addEventListener('click', () => {
          lineupSide = button.dataset.side;
          selectedBenchPlayerId = null;
          render();
        });
      });
      $$('[data-lineup-team]', host).forEach((column) => {
        column.addEventListener('click', () => {
          lineupSide = column.dataset.lineupTeam;
          selectedBenchPlayerId = null;
          render();
        });
      });
      const saveLineup = $('[data-action="save-lineup"]', host);
      if (saveLineup) saveLineup.addEventListener('click', () => {
        if (!Y.hasPermission('lineup:edit:any')) { Y.toast('没有编辑阵容的权限'); return; }
        const result = Y.commitLineup(match, lineupSide, expectedVersion, Y.currentUser().name);
        if (!result.ok) {
          Y.toast(result.message);
          return;
        }
        Y.toast('阵容已保存');
        render();
      });
      const templateButton = $('[data-action="save-template"]', host);
      if (templateButton) {
        templateButton.addEventListener('click', () => {
          const name = window.prompt('模板名称', `${formation.name} 变体`);
          if (!name || !name.trim()) return;
          const base = Y.currentFormation(lineup);
          Y.state.customFormations.push({
            id: 'cf_' + Date.now(),
            name: name.trim(),
            baseId: base.id,
            slots: base.slots.map((slot) => ({ ...slot })),
            createdAt: new Date().toISOString(),
            createdBy: Y.currentUser().name
          });
          const template = Y.state.customFormations[Y.state.customFormations.length - 1];
          Y.addAudit(Y.currentUser().name, '保存阵型模板', name.trim(),
            { after: { templateId: template.id, name: template.name } }, { save: false });
          Y.persistCatalogsAndAudit();
          Y.toast('模板已保存，可在阵型列表中选择');
          render();
        });
      }
      $$('[data-bench-player]', host).forEach((item) => {
        item.addEventListener('click', (event) => {
          if (event.target.closest('[data-bench-remove]')) return;
          selectedBenchPlayerId = Number(item.dataset.benchPlayer);
          render();
        });
        item.addEventListener('dragstart', (event) => {
          event.dataTransfer.setData('text/plain', item.dataset.benchPlayer);
          event.dataTransfer.effectAllowed = 'move';
        });
      });
      $$('[data-bench-remove]', host).forEach((button) => {
        button.addEventListener('click', () => {
          Y.removeFromBench(lineup, Number(button.dataset.benchRemove));
          selectedBenchPlayerId = null;
          render();
        });
      });
      $$('[data-bench-add]', host).forEach((button) => {
        button.addEventListener('click', () => {
          Y.addToBench(lineup, Number(button.dataset.benchAdd));
          render();
        });
      });
      $$('[data-slot]', host).forEach((slot) => {
        slot.addEventListener('click', () => {
          if (opponentSide) return;
          const slotId = slot.dataset.slot;
          if (selectedBenchPlayerId) {
            placePlayer(lineup, slotId, selectedBenchPlayerId);
            selectedBenchPlayerId = null;
            render();
          } else if (lineup.starters[slotId]) {
            const playerId = Number(lineup.starters[slotId]);
            delete lineup.starters[slotId];
            Y.addToBench(lineup, playerId);
            render();
          }
        });
        slot.addEventListener('dragover', (event) => {
          if (opponentSide) return;
          event.preventDefault();
          slot.classList.add('drag-over');
        });
        slot.addEventListener('dragleave', () => slot.classList.remove('drag-over'));
        slot.addEventListener('drop', (event) => {
          event.preventDefault();
          slot.classList.remove('drag-over');
          if (opponentSide) return;
          const slotId = slot.dataset.slot;
          const starterSlot = event.dataTransfer.getData('application/x-starter-slot');
          if (starterSlot) {
            const from = lineup.starters[starterSlot];
            const to = lineup.starters[slotId];
            if (from) {
              lineup.starters[slotId] = from;
              if (to) lineup.starters[starterSlot] = to;
              else delete lineup.starters[starterSlot];
              render();
            }
            return;
          }
          const playerId = Number(event.dataTransfer.getData('text/plain'));
          if (!playerId) return;
          placePlayer(lineup, slotId, playerId);
          render();
        });
      });
      $$('[data-drag-starter]', host).forEach((slot) => {
        slot.addEventListener('dragstart', (event) => {
          event.dataTransfer.setData('application/x-starter-slot', slot.dataset.dragStarter);
          event.dataTransfer.effectAllowed = 'move';
        });
      });
    }

    function placePlayer(lineup, slotId, playerId) {
      Object.keys(lineup.starters).forEach((key) => {
        if (Number(lineup.starters[key]) === Number(playerId)) delete lineup.starters[key];
      });
      const displaced = lineup.starters[slotId];
      lineup.starters[slotId] = Number(playerId);
      if (displaced) Y.addToBench(lineup, displaced);
      Y.removeFromBench(lineup, playerId);
    }

    /* ---------------- Grouping ---------------- */
    function renderGrouping(host) {
      const candidates = Y.state.matches.filter((match) => Y.INTERNAL_TYPES.includes(match.type));
      if (!candidates.length) {
        host.innerHTML = '<div class="empty-state card"><h2>没有队内赛/训练赛</h2><p>对外比赛不参与队内分组。</p></div>';
        return;
      }
      let match = Y.matchById(groupingMatchId);
      if (!match || !Y.INTERNAL_TYPES.includes(match.type)) {
        match = candidates[0];
        groupingMatchId = match.id;
      }
      const grouping = match.grouping;
      const locked = match.status === 'LOCKED';
      const canGenerate = Y.hasPermission('grouping:generate:any');
      const summary = grouping ? Y.groupingSummary(match, grouping) : null;
      const rosterDiff = summary ? summary.rosterDiff : { changed: false, addedIds: [], removedIds: [] };
      const eligibleCount = Y.groupingEligiblePlayers(match).length;
      const registrations = Y.confirmed(match)
        .map((registration) => ({ registration, player: Y.playerById(registration.playerId) }))
        .filter((item) => item.player);
      const groupOf = (playerId) => {
        if (!grouping) return '';
        if ((grouping.A || []).includes(Number(playerId))) return 'A';
        if ((grouping.B || []).includes(Number(playerId))) return 'B';
        return '';
      };
      const ungrouped = registrations.filter(({ player }) => !groupOf(player.id));
      const rosterRow = ({ registration, player }) => {
        const group = groupOf(player.id);
        const groupLabel = group ? Y.groupingTeamName(match, group) : (registration.type === 'GUEST' && !match.includeGuests ? '外挂（本场不参与）' : '未分组');
        return `<div class="reg-admin-row">${Y.avatar(player, 'sm')}<div class="grow"><strong>${esc(player.name)}</strong><small>#${player.jersey ?? '—'} · ${Y.positionLabel(player.position)} · ${registration.type === 'GUEST' ? '外挂' : '球队球员'}</small></div><span class="badge ${group ? 'badge-open' : 'badge-muted'}">${groupLabel}</span></div>`;
      };
      const config = Y.groupingConfig();
      const weightsText = `手动 ${Math.round(config.manualWeight * 100)}% + 自动 ${Math.round(config.autoWeight * 100)}% · 少于 ${config.minAppearances} 场优先手动`;
      const nameOf = (id) => Y.playerById(id)?.name || `#${id}`;
      const warnings = Y.combinedWarnings(grouping, summary);
      host.innerHTML = `<div class="admin-section-head">
        <div><h1>队内分组</h1><p class="muted">按综合分均衡；仅锁定状态可生成、调整和保存。</p></div>
        <div class="table-actions">
          <select class="input input-sm" data-group-match>${candidates.map((item) => `<option value="${item.id}" ${item.id === match.id ? 'selected' : ''}>${esc(item.title)} · ${Y.formatDate(item.start)}</option>`).join('')}</select>
          ${canGenerate ? `<button class="btn btn-primary btn-sm" data-action="generate-grouping"${locked ? '' : ' disabled title="仅锁定状态可生成/重新分组"'}>${grouping ? '重新分组' : '生成分组'}</button>${grouping ? `<button class="btn btn-ghost btn-sm" data-action="clear-grouping"${locked ? '' : ' disabled title="仅锁定状态可清除分组"'}>清除分组</button>` : ''}` : '<span class="muted small">只读</span>'}
        </div>
      </div>
      ${locked ? '' : '<div class="banner banner-warn">只有锁定状态才能进行分组；如需清除或重新分组，请先把比赛状态重置为“锁定”。</div>'}
      ${rosterDiff.changed ? `<div class="banner banner-warn">名单已变化，建议重新生成。新增：${rosterDiff.addedIds.map(nameOf).join('、') || '无'}；移除：${rosterDiff.removedIds.map(nameOf).join('、') || '无'}。</div>` : ''}
      ${warnings.length ? `<div class="banner banner-warn">${warnings.map(esc).join('；')}</div>` : ''}
      <p class="muted small">参与分组 ${eligibleCount} 人 · ${weightsText}${summary ? ` · 总分差 ${summary.diff.toFixed(2)}` : ''}</p>
      ${grouping
        ? `<div class="group-admin-grid">${['A', 'B'].map((team) => groupAdminColumn(match, team, summary, grouping)).join('')}</div>
           <div class="card" style="margin-top:14px"><div class="between"><span class="muted small">保存后不随报名变化自动重算；名单变化时提示重新生成。</span>${canGenerate ? `<button class="btn btn-primary btn-sm" data-action="save-grouping"${locked ? '' : ' disabled'}>保存分组</button>` : '<span class="muted small">只读</span>'}</div></div>`
        : `<div class="empty-state card"><h2>尚未生成</h2><p>${locked ? '点击右上角“生成分组”，按确认名单自动分成两队。' : '比赛锁定后才能生成分组。'}</p></div>`}
      <section class="card" style="margin-top:14px">
        <div class="card-head"><h2>本场报名球员</h2><span class="muted">${registrations.length} 人 · 球队球员 / 外挂</span></div>
        ${grouping ? `<div class="group-admin-grid">
          ${['A', 'B'].map((team) => `<div class="group-column"><div class="group-head"><strong>${Y.groupingTeamName(match, team)}</strong><span>${registrations.filter(({ player }) => groupOf(player.id) === team).length} 人</span></div>${registrations.filter(({ player }) => groupOf(player.id) === team).map(rosterRow).join('') || '<div class="empty-inline">该队暂无球员</div>'}</div>`).join('')}
        </div>
        ${ungrouped.length ? `<div style="margin-top:12px"><h3>未分组 / 不参与</h3><div class="group-roster-list">${ungrouped.map(rosterRow).join('')}</div></div>` : ''}`
        : `<div class="group-roster-list">${registrations.map(rosterRow).join('') || '<div class="empty-inline">本场暂无确认报名球员</div>'}</div>`}
      </section>`;
      $('[data-group-match]', host).addEventListener('change', (event) => {
        groupingMatchId = event.target.value;
        render();
      });
      const generate = $('[data-action="generate-grouping"]', host);
      if (generate) {
        generate.addEventListener('click', () => {
          if (!locked) {
            Y.toast('只有锁定状态才能进行分组');
            return;
          }
          if (match.grouping && !window.confirm('重新生成会覆盖当前分组，继续？')) return;
          const result = Y.buildGrouping(match);
          if (!result.ok) {
            Y.toast(result.message);
            return;
          }
          match.grouping = result.grouping;
          Y.save();
          Y.addAudit(Y.currentUser().name, '生成分组', match.title, { after: { warnings: result.grouping.warnings } });
          Y.toast(result.grouping.warnings[0] || '分组已生成');
          render();
        });
      }
      const save = $('[data-action="save-grouping"]', host);
      if (save) {
        save.addEventListener('click', () => {
          if (!locked) {
            Y.toast('只有锁定状态才能保存分组');
            return;
          }
          const warningText = warnings.length ? `（${warnings.join('；')}）` : '';
          if ((rosterDiff.changed || warnings.length) &&
            !window.confirm(`名单已变化或存在约束问题${warningText}，仍要保存当前分组？`)) return;
          const result = Y.saveGroupingSnapshot(match, Y.currentUser().name);
          Y.toast(result.ok ? '分组已保存' : result.message);
          if (result.ok) render();
        });
      }
      const clear = $('[data-action="clear-grouping"]', host);
      if (clear) {
        clear.addEventListener('click', () => {
          if (!locked) { Y.toast('只有锁定状态才能清除分组'); return; }
          if (!window.confirm('清除当前分组后可重新生成，确认继续？')) return;
          match.grouping = null;
          Y.save();
          Y.addAudit(Y.currentUser().name, '清除分组', match.title);
          Y.toast('分组已清除，可重新生成');
          render();
        });
      }
      $$('[data-group-player]', host).forEach((item) => {
        item.addEventListener('dragstart', (event) => {
          if (!locked) {
            event.preventDefault();
            return;
          }
          event.dataTransfer.setData('application/x-group-player', item.dataset.groupPlayer);
          event.dataTransfer.setData('application/x-group-from', item.dataset.from);
          event.dataTransfer.effectAllowed = 'move';
        });
      });
      $$('[data-drop-team]', host).forEach((column) => {
        column.addEventListener('dragover', (event) => {
          event.preventDefault();
          column.classList.add('drag-over');
        });
        column.addEventListener('dragleave', () => column.classList.remove('drag-over'));
        column.addEventListener('drop', (event) => {
          event.preventDefault();
          column.classList.remove('drag-over');
          if (!locked) {
            Y.toast('只有锁定状态才能调整分组');
            return;
          }
          const playerId = Number(event.dataTransfer.getData('application/x-group-player'));
          const from = event.dataTransfer.getData('application/x-group-from');
          const to = column.dataset.dropTeam;
          if (!playerId || !from || from === to) return;
          const groupedMatch = Y.matchById(groupingMatchId);
          groupedMatch.grouping[from] = groupedMatch.grouping[from].filter((id) => id !== playerId);
          groupedMatch.grouping[to].push(playerId);
          groupedMatch.grouping.savedAt = null;
          groupedMatch.grouping.snapshot = null;
          Y.save();
          render();
        });
      });
    }

    function groupAdminColumn(match, team, summary, grouping) {
      const ids = (grouping && grouping[team]) || [];
      const players = ids.map((id) => Y.playerById(id)).filter(Boolean);
      const total = team === 'A' ? (summary ? summary.totalA : 0) : (summary ? summary.totalB : 0);
      const pos = team === 'A' ? (summary ? summary.posA : {}) : (summary ? summary.posB : {});
      const posText = Y.GROUP_POSITIONS
        .map((key) => `${Y.positionLabel(key)} ${pos[key] || 0}`).join(' · ');
      const scoreOf = (id) => (grouping && grouping.scores && grouping.scores[id]) || null;
      return `<div class="group-admin-column" data-drop-team="${team}">
        <div class="group-admin-head"><strong>${Y.groupingTeamName(match, team)}</strong><span>${players.length} 人 · 综合 ${total.toFixed(2)}</span><small>${posText}</small></div>
        ${players.map((player) => {
          const score = scoreOf(player.id);
          const detail = score
            ? `${Y.positionShort(player.position)} · 手动 ${score.manual ?? '—'}★ · 自动 ${score.auto ? score.auto.toFixed(1) : '—'} · 综合 ${Number(score.final).toFixed(2)}${score.manualOnly ? '（以手动分为准）' : ''}`
            : `${Y.positionShort(player.position)} · ${player.skill}★`;
          return `<div class="group-player" draggable="true" data-group-player="${player.id}" data-from="${team}">${Y.avatar(player, 'xs')}<span>${esc(player.name)}</span><small>${detail}</small></div>`;
        }).join('') || '<div class="empty-inline">拖球员到这里</div>'}
      </div>`;
    }

    /* ---------------- Players ---------------- */
    function renderPlayers(host) {
      const players = Y.state.players.slice().sort((a, b) => {
        if (a.type !== b.type) return a.type === 'PLAYER' ? -1 : 1;
        return a.jersey - b.jersey;
      });
      const selected = (field, value) => playerFilters[field] === value ? ' selected' : '';
      host.innerHTML = `<div class="admin-section-head">
        <div><h1>球员</h1><p class="muted">审核、外挂转正、注销；历史数据保留。</p></div>
      </div>
      <form class="schedule-filters" data-player-filter-form>
        <label class="filter-field grow"><span>姓名</span><input class="input input-sm" data-player-filter="name" placeholder="搜索球员姓名" value="${esc(playerFilters.name)}"></label>
        <label class="filter-field"><span>地区</span><input class="input input-sm" data-player-filter="region" placeholder="搜索地区" value="${esc(playerFilters.region)}"></label>
        <label class="filter-field"><span>类型</span><select class="input input-sm" data-player-filter="type"><option value="ALL">全部</option><option value="PLAYER"${selected('type', 'PLAYER')}>正式</option><option value="GUEST"${selected('type', 'GUEST')}>外挂</option></select></label>
        <label class="filter-field"><span>管理员</span><select class="input input-sm" data-player-filter="admin"><option value="ALL">全部</option><option value="YES"${selected('admin', 'YES')}>是</option><option value="NO"${selected('admin', 'NO')}>否</option></select></label>
        <label class="filter-field"><span>状态</span><select class="input input-sm" data-player-filter="status"><option value="ALL">全部</option><option value="ACTIVE"${selected('status', 'ACTIVE')}>正常</option><option value="PENDING"${selected('status', 'PENDING')}>待审核</option><option value="DISABLED"${selected('status', 'DISABLED')}>已注销</option></select></label>
        <label class="filter-field"><span>位置</span><select class="input input-sm" data-player-filter="position"><option value="ALL">全部</option>${Y.GROUP_POSITIONS.map((code) => `<option value="${code}"${selected('position', code)}>${Y.positionLabel(code)}</option>`).join('')}</select></label>
        <label class="filter-field"><span>星级</span><select class="input input-sm" data-player-filter="skill"><option value="ALL">全部</option>${[1, 2, 3, 4, 5].map((value) => `<option value="${value}"${selected('skill', String(value))}>${value} 星</option>`).join('')}</select></label>
        <label class="filter-field"><span>排序</span><select class="input input-sm" data-player-sort>
          ${[['default', '默认（号码 ↑）'], ['jersey:asc', '号码 ↑'], ['jersey:desc', '号码 ↓'], ['skill:desc', '星级 ↓'], ['skill:asc', '星级 ↑'], ['position:asc', '位置 ↑'], ['position:desc', '位置 ↓'], ['region:asc', '地区 ↑'], ['region:desc', '地区 ↓']].map(([value, label]) => `<option value="${value}"${playerSort === value ? ' selected' : ''}>${label}</option>`).join('')}
        </select></label>
        <button class="btn btn-primary btn-sm" type="submit" data-player-search>搜索</button>
        <button class="btn btn-ghost btn-sm" type="button" data-player-reset>重置</button>
      </form>
      <div class="table-wrap" data-players-table></div>`;

      function renderTable() {
        const term = playerFilters.name.trim().toLowerCase();
        const list = players.filter((player) => {
          if (term && !player.name.toLowerCase().includes(term)) return false;
          if (playerFilters.region.trim() && !(player.region || '').toLowerCase().includes(playerFilters.region.trim().toLowerCase())) return false;
          if (playerFilters.type !== 'ALL' && player.type !== playerFilters.type) return false;
          if (playerFilters.admin !== 'ALL' && (Y.rolesOf(player).includes('ADMIN') ? 'YES' : 'NO') !== playerFilters.admin) return false;
          if (playerFilters.status !== 'ALL' && player.status !== playerFilters.status) return false;
          if (playerFilters.position !== 'ALL' && player.position !== playerFilters.position) return false;
          if (playerFilters.skill !== 'ALL' && Number(player.skill) !== Number(playerFilters.skill)) return false;
          return true;
        });
        const [sortKey, sortDir] = playerSort.split(':');
        if (sortKey === 'jersey') list.sort((a, b) => a.jersey - b.jersey);
        else if (sortKey === 'skill') list.sort((a, b) => a.skill - b.skill || Y.positionRank(a.position) - Y.positionRank(b.position));
        else if (sortKey === 'position') list.sort((a, b) => Y.positionRank(a.position) - Y.positionRank(b.position));
        else if (sortKey === 'region') list.sort((a, b) => (a.region || '').localeCompare(b.region || '', 'zh-CN'));
        else list.sort((a, b) => a.jersey - b.jersey);
        if (sortDir === 'desc') list.reverse();
        const paged = Y.paginate(list, playerPage, playerPageSize);
        playerPage = paged.page;
        const canEdit = Y.hasPermission('player:edit:any');
        const canApprove = Y.hasPermission('user:approve:any');
        $('[data-players-table]', host).innerHTML = `<table class="data-table">
          <thead><tr><th>号码</th><th>头像</th><th>姓名</th><th>地区</th><th>类型</th><th>管理员</th><th>状态</th><th>位置</th><th>星级</th><th>微信</th><th>操作</th></tr></thead>
          <tbody>${paged.items.map((player) => `<tr>
            <td>#${player.jersey}</td>
            <td>${Y.avatar(player, 'sm')}</td>
            <td><strong>${esc(player.name)}</strong></td>
            <td>${esc(player.region || '—')}</td>
            <td>${player.type === 'PLAYER' ? '正式' : '外挂'}</td>
            <td>${Y.rolesOf(player).includes('ADMIN') ? '是' : '否'}</td>
            <td><span class="badge ${player.status === 'ACTIVE' ? 'badge-open' : player.status === 'PENDING' ? 'badge-postponed' : 'badge-cancelled'}">${player.status === 'ACTIVE' ? '正常' : player.status === 'PENDING' ? '待审核' : '已注销'}</span></td>
            <td>${Y.positionLabel(player.position)}</td>
            <td>${Y.stars(player.skill)}</td>
            <td>${player.wechat ? '已绑定' : '未绑定'}</td>
            <td><div class="table-actions">
              ${canEdit ? `<button class="btn btn-ghost btn-sm" data-player-action="edit" data-player-id="${player.id}">编辑</button>` : ''}
              ${canEdit && player.wechat ? `<button class="btn btn-ghost btn-sm" data-player-action="wechat-unbind" data-player-id="${player.id}">解绑微信</button>` : ''}
              ${canApprove && player.status === 'PENDING' ? `<button class="btn btn-primary btn-sm" data-player-action="approve" data-player-id="${player.id}">通过</button>` : ''}
              ${canEdit && player.type === 'GUEST' && player.status === 'ACTIVE' ? `<button class="btn btn-ghost btn-sm" data-player-action="convert" data-player-id="${player.id}">转正</button>` : ''}
              ${canEdit && player.status === 'ACTIVE' ? `<button class="btn btn-ghost btn-sm" data-player-action="disable" data-player-id="${player.id}">注销</button>` : ''}
            </div></td>
          </tr>`).join('') || '<tr><td colspan="11" class="muted">没有符合条件的球员</td></tr>'}</tbody>
        </table>${Y.paginationHtml(paged.page, paged.totalPages, paged.total, playerPageSize)}`;
        $$('[data-players-table] [data-page]', host).forEach((button) => {
          button.addEventListener('click', () => {
            playerPage = Number(button.dataset.page);
            renderTable();
          });
        });
        $('[data-players-table] [data-page-size]', host).addEventListener('change', (event) => {
          playerPageSize = Number(event.target.value);
          playerPage = 1;
          renderTable();
        });
        $$('[data-player-action]', host).forEach((button) => {
          button.addEventListener('click', () => {
            const player = Y.playerById(Number(button.dataset.playerId));
            const action = button.dataset.playerAction;
            if (!player) return;
            if (action === 'approve' && !Y.hasPermission('user:approve:any')) { Y.toast('没有审核用户的权限'); return; }
            if (action !== 'approve' && !Y.hasPermission('player:edit:any')) { Y.toast('没有编辑球员的权限'); return; }
            if (action === 'edit') {
              openPlayerEditor(player);
              return;
            }
            if (action === 'approve') {
              if (player.status !== 'PENDING') { Y.toast('仅待审核账号可通过审核'); return; }
              player.status = 'ACTIVE';
              Y.notify(player.id, '审核通过', '你的账号已审核通过，现在可以报名和签到。');
            }
            if (action === 'convert') {
              if (player.status !== 'ACTIVE' || player.type !== 'GUEST') { Y.toast('仅正常状态的外挂球员可转正'); return; }
              const duplicateJersey = Y.state.players.some((item) => item.id !== player.id && item.status === 'ACTIVE' && item.type === 'PLAYER' && Number(item.jersey) === Number(player.jersey));
              if (duplicateJersey) { Y.toast('球衣号已被正式球员占用，请先修改号码'); return; }
              player.type = 'PLAYER';
              player.roles = ['PLAYER'];
              player.admin = false;
            }
            if (action === 'disable') {
              if (player.status !== 'ACTIVE') { Y.toast('仅正常状态账号可注销'); return; }
              const activeManagers = Y.state.players.filter((item) => Y.isActiveManager(item));
              const isManager = Y.isActiveManager(player);
              if (isManager && activeManagers.length <= 1) {
                Y.toast('不能注销最后一个管理员');
                return;
              }
              const reason = window.prompt('请填写注销原因（写入审计）');
              if (reason === null) return;
              if (!reason.trim()) { Y.toast('注销原因不能为空'); return; }
              player.status = 'DISABLED';
              player.disableReason = reason.trim();
              Y.cancelPlayerRegistrations(player.id, Y.currentUser().name);
              Y.notify(player.id, '账号已注销', '你的账号已被注销，不能继续登录或报名。');
              if (Number(Y.currentUser().id) === Number(player.id)) Y.setUser(0);
            }
            if (action === 'wechat-unbind') {
              if (!window.confirm('确认解绑该球员的微信？解绑后需重新授权才能绑定。')) return;
              player.wechat = false;
            }
            const labels = { approve: '审核通过', convert: '外挂转正', disable: '注销球员', 'wechat-unbind': '解绑微信' };
            Y.save();
            Y.addAudit(Y.currentUser().name, labels[action] || action, player.name + (action === 'disable' ? ` / ${player.disableReason}` : ''));
            Y.toast('已更新');
            render();
          });
        });
      }

      $('[data-player-filter-form]', host).addEventListener('submit', (event) => {
        event.preventDefault();
        $$('[data-player-filter]', host).forEach((control) => {
          playerFilters[control.dataset.playerFilter] = control.value;
        });
        playerSort = $('[data-player-sort]', host).value;
        playerPage = 1;
        renderTable();
      });
      $('[data-player-reset]', host).addEventListener('click', () => {
        playerFilters = { name: '', region: '', type: 'ALL', admin: 'ALL', status: 'ALL', position: 'ALL', skill: 'ALL' };
        playerSort = 'default';
        playerPage = 1;
        render();
      });
      renderTable();
    }

    function openPlayerEditor(player) {
      const positions = Y.GROUP_POSITIONS.map((code) => [code, Y.positionLabel(code)]);
      const secondary = Array.isArray(player.secondary) ? player.secondary : [];
      let pendingAvatar = player.avatar || '';
      const body = `<form id="player-form" class="form-grid">
        <div class="full table-actions">
          <span data-avatar-preview>${Y.avatar(player, 'xl')}</span>
          <label class="btn btn-ghost btn-sm">上传头像<input type="file" accept="image/*" data-avatar-file hidden></label>
          <button type="button" class="btn btn-ghost btn-sm" data-avatar-remove>移除头像</button>
          <span class="muted small">支持 JPG/PNG/WebP，最大 2MB，自动压缩</span>
        </div>
        <label>姓名<input class="input" name="name" value="${esc(player.name)}" required></label>
        <label>号码<input class="input" type="number" name="jersey" min="0" value="${player.jersey}" required></label>
        <label>地区<input class="input" name="region" value="${esc(player.region || '')}"></label>
        <label>主位置<select class="input" name="position">${positions.map(([value, label]) => `<option value="${value}"${player.position === value ? ' selected' : ''}>${label}</option>`).join('')}</select></label>
        <label>星级<select class="input" name="skill">${[1, 2, 3, 4, 5].map((value) => `<option value="${value}"${Number(player.skill) === value ? ' selected' : ''}>${value} 星</option>`).join('')}</select></label>
        <div class="full"><span class="muted small">副位置</span><div class="chip-row">${positions.map(([value, label]) => `<label class="checkline"><input type="checkbox" name="secondary" value="${value}"${secondary.includes(value) ? ' checked' : ''}${player.position === value ? ' disabled' : ''}> ${label}</label>`).join('')}</div></div>
      </form>`;
      const modal = Y.openModal('编辑球员', body, '<button class="btn btn-primary" id="player-submit">保存</button><button class="btn btn-ghost" data-close-modal>取消</button>');
      const preview = modal.querySelector('[data-avatar-preview]');
      const paintPreview = () => { preview.innerHTML = Y.avatar({ ...player, avatar: pendingAvatar }, 'xl'); };
      modal.querySelector('[data-avatar-file]').addEventListener('change', async (event) => {
        const file = event.target.files && event.target.files[0];
        if (!file) return;
        try {
          pendingAvatar = await Y.readAvatarFile(file);
          paintPreview();
        } catch (error) {
          Y.toast(error.message || '头像上传失败');
        }
      });
      modal.querySelector('[data-avatar-remove]').addEventListener('click', () => {
        pendingAvatar = '';
        paintPreview();
      });
      modal.querySelector('#player-submit').addEventListener('click', () => {
        const form = modal.querySelector('#player-form');
        if (!form.reportValidity()) return;
        const data = new FormData(form);
        const jersey = Number(data.get('jersey'));
        const duplicate = player.type === 'PLAYER' && Y.state.players.some((item) => item.id !== player.id && item.status === 'ACTIVE' && item.type === 'PLAYER' && Number(item.jersey) === jersey);
        if (duplicate) {
          Y.toast('球衣号已被其他正式球员占用，请修改号码');
          return;
        }
        const secondaryCodes = Array.from(data.getAll('secondary')).filter((code) => code !== String(data.get('position')));
        const result = Y.updatePlayer(player.id, {
          name: String(data.get('name')),
          jersey,
          region: String(data.get('region') || ''),
          position: String(data.get('position')),
          secondary: secondaryCodes,
          skill: Number(data.get('skill')),
          avatar: pendingAvatar
        }, Y.currentUser().name);
        if (!result.ok) {
          Y.toast(result.message);
          return;
        }
        Y.closeModal();
        Y.toast('球员已更新');
        render();
      });
    }

    /* ---------------- Roles / permissions ---------------- */
    function renderRoles(host) {
      const roles = Y.state.roles;
      if (!roles.some((role) => role.code === selectedRoleCode)) selectedRoleCode = roles[0].code;
      const role = roles.find((item) => item.code === selectedRoleCode);
      const users = Y.state.players.filter((player) => player.status !== 'DISABLED');
      if (!users.some((user) => String(user.id) === String(selectedUserId))) selectedUserId = String(users[0].id);
      const user = users.find((item) => String(item.id) === String(selectedUserId));
      const userRoles = Y.rolesOf(user);
      const canManage = Y.hasPermission('role:manage:any');
      const groups = {};
      Y.state.permissions.forEach((permission) => {
        (groups[permission.group] = groups[permission.group] || []).push(permission);
      });
      host.innerHTML = `<div class="admin-section-head">
        <div><h1>角色权限</h1><p class="muted">默认角色不可删；管理员必须叠加正式球员；外挂与正式互斥。</p></div>
        <span class="badge badge-soon">原型以角色身份模拟权限</span>
      </div>
      <div class="role-layout">
        <aside class="role-list">
          ${canManage ? '<button class="btn btn-ghost btn-sm" data-action="create-role" style="margin-bottom:8px">+ 新建角色</button>' : ''}
          ${roles.map((item) => `<button class="role-item ${item.code === selectedRoleCode ? 'active' : ''}" data-role="${item.code}">
          <span><strong>${item.name}</strong><small>${item.code}${item.isDefault ? ' · 默认' : ' · 自定义'}</small></span>
          <span class="badge ${item.isDefault ? 'badge-open' : 'badge-muted'}">${item.isDefault ? '默认' : '自定义'}</span>
        </button>`).join('')}</aside>
        <section class="permission-panel">
          <div class="card-head"><h2>${role.name} 的权限</h2>
            <div class="table-actions">
              ${canManage ? `<button class="btn btn-ghost btn-sm" data-action="rename-role">重命名</button>${role.isDefault ? '' : '<button class="btn btn-ghost btn-sm" data-action="delete-role">删除</button>'}` : ''}
              ${canManage ? '<button class="btn btn-primary btn-sm" data-action="save-role">保存权限</button>' : '<span class="muted small">只读</span>'}
            </div>
          </div>
          <div class="permission-groups">${Object.entries(groups).map(([group, items]) => `<div class="permission-group">
            <h4>${group}</h4>
            ${items.map((permission) => `<label class="permission-item"><input type="checkbox" value="${permission.code}" data-permission ${role.permissions.includes(permission.code) ? 'checked' : ''}${canManage ? '' : ' disabled'}><span>${permission.label}<br><code>${permission.code}</code></span></label>`).join('')}
          </div>`).join('')}</div>
          <div class="user-role-box">
            <label>给用户分配角色
              <select class="input" data-user-select>${users.map((item) => `<option value="${item.id}" ${String(item.id) === String(selectedUserId) ? 'selected' : ''}>${esc(item.name)}（${Y.roleLabel(Y.userRole(item))}）</option>`).join('')}</select>
            </label>
            <div>
              <div class="role-checks">${roles.map((item) => `<label><input type="checkbox" value="${item.code}" data-user-role ${userRoles.includes(item.code) ? 'checked' : ''}${canManage ? '' : ' disabled'}>${item.name}</label>`).join('')}</div>
              ${canManage ? '<button class="btn btn-primary btn-sm" data-action="save-user-roles" style="margin-top:10px">保存用户角色</button>' : '<p class="muted small" style="margin-top:10px">只读：没有管理角色的权限。</p>'}
            </div>
          </div>
        </section>
      </div>`;
      $$('[data-role]', host).forEach((button) => {
        button.addEventListener('click', () => {
          selectedRoleCode = button.dataset.role;
          render();
        });
      });
      const createRole = $('[data-action="create-role"]', host);
      if (createRole) createRole.addEventListener('click', () => {
        if (!Y.hasPermission('role:manage:any')) { Y.toast('没有管理角色的权限'); return; }
        const modal = Y.openModal('新建角色', `<form id="role-form" class="form-grid">
          <label>角色编码<input class="input" name="code" placeholder="例如 COACH" required></label>
          <label>角色名称<input class="input" name="name" placeholder="例如 教练" required></label>
          <p class="muted small full">编码仅限大写字母、数字和下划线；新角色默认无权限，创建后在右侧勾选。</p>
        </form>`, '<button class="btn btn-primary" id="role-create-submit">创建</button><button class="btn btn-ghost" data-close-modal>取消</button>');
        modal.querySelector('#role-create-submit').addEventListener('click', () => {
          const form = modal.querySelector('#role-form');
          if (!form.reportValidity()) return;
          const data = new FormData(form);
          const code = String(data.get('code') || '').trim().toUpperCase();
          const name = String(data.get('name') || '').trim();
          if (!/^[A-Z][A-Z0-9_]*$/.test(code)) {
            Y.toast('角色编码仅限大写字母、数字和下划线');
            return;
          }
          if (Y.state.roles.some((item) => item.code === code)) {
            Y.toast('角色编码已存在');
            return;
          }
          Y.state.roles.push({ code, name, isDefault: false, permissions: [] });
          selectedRoleCode = code;
          Y.save();
          Y.addAudit(Y.currentUser().name, '新建角色', `${code} / ${name}`);
          Y.closeModal();
          Y.toast('角色已创建');
          render();
        });
      });
      const renameRoleButton = $('[data-action="rename-role"]', host);
      if (renameRoleButton) renameRoleButton.addEventListener('click', () => {
        if (!Y.hasPermission('role:manage:any')) { Y.toast('没有管理角色的权限'); return; }
        const name = window.prompt('角色名称', role.name);
        if (!name || !name.trim()) return;
        const before = role.name;
        role.name = name.trim();
        Y.save();
        Y.addAudit(Y.currentUser().name, '重命名角色', `${role.code}：${before} → ${role.name}`, { before: { name: before }, after: { name: role.name } });
        Y.toast('角色已重命名');
        render();
      });
      const deleteRoleButton = $('[data-action="delete-role"]', host);
      if (deleteRoleButton) deleteRoleButton.addEventListener('click', () => {
        if (!Y.hasPermission('role:manage:any')) { Y.toast('没有管理角色的权限'); return; }
        if (role.isDefault) {
          Y.toast('默认角色不可删除');
          return;
        }
        if (!window.confirm(`确认删除角色“${role.name}”？`)) return;
        const assigned = Y.state.players.some((player) => Y.rolesOf(player).includes(role.code));
        if (assigned) {
          Y.toast('仍有用户拥有该角色，不能删除');
          return;
        }
        Y.state.roles = Y.state.roles.filter((item) => item.code !== role.code);
        Y.state.players.forEach((player) => {
          if (!Array.isArray(player.roles)) return;
          player.roles = player.roles.filter((code) => code !== role.code);
          if (!player.roles.length) player.roles = [player.type === 'GUEST' ? 'GUEST' : 'PLAYER'];
          player.admin = player.roles.includes('ADMIN');
        });
        selectedRoleCode = Y.state.roles[0].code;
        Y.save();
        Y.addAudit(Y.currentUser().name, '删除角色', role.code);
        Y.toast('角色已删除');
        render();
      });
      $('[data-user-select]', host).addEventListener('change', (event) => {
        selectedUserId = event.target.value;
        render();
      });
      const saveRole = $('[data-action="save-role"]', host);
      if (saveRole) saveRole.addEventListener('click', () => {
        if (!Y.hasPermission('role:manage:any')) { Y.toast('没有管理角色的权限'); return; }
        const checked = role.code === 'ADMIN'
          ? Y.state.permissions.map((permission) => permission.code)
          : $$('[data-permission]', host).filter((input) => input.checked).map((input) => input.value);
        const previous = role.permissions.slice();
        role.permissions = checked;
        const managers = Y.state.players.filter((player) => Y.isActiveManager(player));
        if (!managers.length) {
          role.permissions = previous;
          Y.toast('系统必须至少保留一个拥有 role:manage 权限的用户');
          return;
        }
        Y.save();
        Y.addAudit(Y.currentUser().name, '调整角色权限', role.code);
        Y.toast('权限已保存');
        render();
      });
      const saveUserRoles = $('[data-action="save-user-roles"]', host);
      if (saveUserRoles) saveUserRoles.addEventListener('click', () => {
        if (!Y.hasPermission('role:manage:any')) { Y.toast('没有管理角色的权限'); return; }
        const checked = $$('[data-user-role]', host).filter((input) => input.checked).map((input) => input.value);
        if (!checked.length) {
          Y.toast('用户至少需要一个角色');
          return;
        }
        if (checked.includes('GUEST') && checked.includes('PLAYER')) {
          Y.toast('外挂与正式球员互斥');
          return;
        }
        if (checked.includes('ADMIN') && !checked.includes('PLAYER')) {
          Y.toast('管理员必须同时拥有正式球员角色');
          return;
        }
        if (checked.includes('PLAYER')) {
          const duplicateJersey = Y.state.players.some((item) =>
            item.id !== user.id && item.status === 'ACTIVE' && item.type === 'PLAYER' && Number(item.jersey) === Number(user.jersey));
          if (duplicateJersey) {
            Y.toast('该球员号码已被其他正式球员占用，不能授予 PLAYER 角色');
            return;
          }
        }
        const remainingManagers = Y.state.players.filter((item) =>
          Y.isActiveManager(item, item.id === user.id ? checked : undefined)
        );
        if (!remainingManagers.length) {
          Y.toast('系统必须至少保留一个拥有 role:manage 权限的用户');
          return;
        }
        user.roles = checked;
        user.admin = checked.includes('ADMIN');
        user.type = checked.includes('GUEST') && !checked.includes('PLAYER') ? 'GUEST' : 'PLAYER';
        Y.save();
        Y.notify(user.id, '角色变更', `你的角色已更新为：${checked.join(' / ')}`);
        Y.addAudit(Y.currentUser().name, '分配用户角色', `${user.name} → ${checked.join(' / ')}`);
        Y.toast('用户角色已保存');
        render();
      });
    }

    /* ---------------- Settings ---------------- */
    function renderInvites(host) {
      if (!Y.hasPermission('invite:manage:any')) {
        host.innerHTML = '<div class="card"><div class="empty-inline">没有管理邀请的权限。</div></div>';
        return;
      }
      if (!Array.isArray(Y.state.invites)) Y.state.invites = [];
      const rows = Y.state.invites.slice().sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
      host.innerHTML = `<div class="admin-section-head">
        <div><h1>邀请管理</h1><p class="muted">入队邀请与比赛邀请；默认 7 天有效，默认不限次数。</p></div>
        <button class="btn btn-primary" data-action="create-invite">+ 创建邀请</button>
      </div>
      <div class="table-wrap"><table class="data-table">
        <thead><tr><th>类型</th><th>关联比赛</th><th>邀请人</th><th>有效期</th><th>已用/上限</th><th>状态</th><th>操作</th></tr></thead>
        <tbody>${rows.map((invite) => {
          const match = invite.matchId ? Y.matchById(invite.matchId) : null;
          return `<tr>
            <td>${invite.type === 'TEAM' ? '入队邀请' : '比赛邀请'}</td>
            <td>${match ? esc(match.title) : '—'}</td>
            <td>${esc(invite.inviter || '—')}</td>
            <td>${Y.formatDateTime(invite.expiresAt)}</td>
            <td>${invite.usedCount || 0}/${invite.maxUses == null ? '不限' : invite.maxUses}</td>
            <td><span class="badge ${invite.status === 'ACTIVE' ? 'badge-open' : 'badge-cancelled'}">${invite.status === 'ACTIVE' ? '有效' : '已撤销'}</span></td>
            <td><div class="table-actions">
              ${invite.status === 'ACTIVE' ? `<button class="btn btn-ghost btn-sm" data-invite-copy="${invite.id}">复制链接</button><button class="btn btn-ghost btn-sm" data-invite-revoke="${invite.id}">撤销</button>` : '—'}
            </div></td>
          </tr>`;
        }).join('') || '<tr><td colspan="7"><div class="empty-inline">暂无邀请</div></td></tr>'}</tbody>
      </table></div>`;
      $('[data-action="create-invite"]', host).addEventListener('click', openInviteModal);
      $$('[data-invite-copy]', host).forEach((button) => {
        button.addEventListener('click', async () => {
          const invite = Y.state.invites.find((item) => item.id === button.dataset.inviteCopy);
          if (!invite) return;
          const url = `invite.html?token=${encodeURIComponent(invite.token)}`;
          try { await navigator.clipboard.writeText(url); Y.toast('邀请链接已复制'); }
          catch { Y.toast(url); }
        });
      });
      $$('[data-invite-revoke]', host).forEach((button) => {
        button.addEventListener('click', () => {
          if (!window.confirm('确认撤销该邀请？撤销后立即失效。')) return;
          const invite = Y.state.invites.find((item) => item.id === button.dataset.inviteRevoke);
          if (!invite) return;
          invite.status = 'REVOKED';
          Y.save();
          Y.addAudit(Y.currentUser().name, '撤销邀请', invite.id);
          Y.toast('邀请已撤销');
          render();
        });
      });
    }

    function openInviteModal() {
      const matchOptions = Y.matchesByStartDesc()
        .filter((match) => ['SIGNUP_OPEN', 'SIGNUP_CLOSED', 'LOCKED'].includes(match.status))
        .map((match) => `<option value="${match.id}">${esc(match.title)} · ${Y.formatDate(match.start)}</option>`).join('');
      const body = `<form id="invite-form" class="form-grid">
        <label>邀请类型<select class="input" name="type"><option value="TEAM">入队邀请</option><option value="MATCH">比赛邀请</option></select></label>
        <label>关联比赛<select class="input" name="matchId"><option value="">—</option>${matchOptions}</select></label>
        <label>有效期（天）<input class="input" type="number" name="expiresDays" value="7" min="1" max="30"></label>
        <label>使用上限<input class="input" type="number" name="maxUses" min="1" placeholder="留空表示不限"></label>
        <p class="muted small full">入队邀请注册免审为 PLAYER；比赛邀请只引导报名，不改变账号状态。</p>
      </form>`;
      const modal = Y.openModal('创建邀请', body, '<button class="btn btn-primary" id="invite-submit">创建</button><button class="btn btn-ghost" data-close-modal>取消</button>');
      modal.querySelector('#invite-submit').addEventListener('click', () => {
        const form = modal.querySelector('#invite-form');
        if (!form.reportValidity()) return;
        const data = new FormData(form);
        const type = String(data.get('type'));
        const matchId = type === 'MATCH' ? String(data.get('matchId') || '') : '';
        if (type === 'MATCH' && !matchId) { Y.toast('比赛邀请必须选择比赛'); return; }
        const days = Number(data.get('expiresDays'));
        const maxUses = data.get('maxUses') === '' ? null : Number(data.get('maxUses'));
        const expiresAt = new Date(Date.now() + Math.max(1, days) * 24 * 60 * 60 * 1000).toISOString();
        const invite = {
          id: 'inv' + Date.now(),
          type,
          matchId: matchId || null,
          inviter: Y.currentUser().name,
          expiresAt,
          maxUses,
          usedCount: 0,
          status: 'ACTIVE',
          token: Math.random().toString(36).slice(2) + Date.now().toString(36),
          createdAt: new Date().toISOString()
        };
        Y.state.invites.push(invite);
        Y.save();
        Y.addAudit(Y.currentUser().name, '创建邀请', `${type} / ${invite.id}`);
        Y.closeModal();
        Y.toast('邀请已创建');
        render();
      });
    }

    function renderSettings(host) {
      const settings = Y.state.settings;
      host.innerHTML = `<div class="admin-section-head">
        <div><h1>系统设置</h1><p class="muted">报名默认参数；新建比赛时自动带入，可在比赛里覆盖。</p></div>
      </div>
      <section class="admin-form-card" style="max-width:760px">
        <div class="admin-form-row">
          <label>正式球员报名上限<input class="input" type="number" min="1" data-setting="maxPlayers" value="${settings.maxPlayers}"></label>
          <label>外挂球员报名上限<input class="input" type="number" min="0" data-setting="maxGuests" value="${settings.maxGuests}"></label>
          <label>候补上限<input class="input" type="number" min="0" data-setting="maxWaitlist" value="${settings.maxWaitlist}"></label>
        </div>
        <div class="admin-form-row" style="margin-top:12px">
          <label>分组手动权重<input class="input" type="number" min="0" step="1" data-setting="groupingManualWeight" value="${settings.groupingManualWeight ?? 70}"></label>
          <label>分组自动权重<input class="input" type="number" min="0" step="1" data-setting="groupingAutoWeight" value="${settings.groupingAutoWeight ?? 30}"></label>
          <label>自动分最少出场<input class="input" type="number" min="0" step="1" data-setting="groupingMinAppearances" value="${settings.groupingMinAppearances ?? 3}"></label>
        </div>
        <div class="admin-form-row" style="margin-top:12px">
          <label>出场率权重<input class="input" type="number" min="0" step="1" data-setting="groupingAppearanceWeight" value="${settings.groupingAppearanceWeight ?? 50}"></label>
          <label>进球权重<input class="input" type="number" min="0" step="1" data-setting="groupingGoalWeight" value="${settings.groupingGoalWeight ?? 25}"></label>
          <label>助攻权重<input class="input" type="number" min="0" step="1" data-setting="groupingAssistWeight" value="${settings.groupingAssistWeight ?? 25}"></label>
        </div>
        <p class="muted small" style="margin-top:10px">候补上限按全场合计；修改只影响新建比赛，已创建比赛保持原参数。分组权重按比例生效，默认 70/30；自动分默认出场 50/进球 25/助攻 25；少于最少出场时优先手动分。已保存分组展示当前配置权重，分数不回算。</p>
        ${Y.hasPermission('settings:manage:any') ? '<button class="btn btn-primary btn-sm" data-action="save-settings" style="margin-top:12px">保存设置</button>' : '<p class="muted small" style="margin-top:12px">只读：没有管理系统设置的权限。</p>'}
      </section>`;
      const saveSettings = $('[data-action="save-settings"]', host);
      if (saveSettings) saveSettings.addEventListener('click', () => {
        if (!Y.hasPermission('settings:manage:any')) { Y.toast('没有管理系统设置的权限'); return; }
        const next = {};
        $$('[data-setting]', host).forEach((input) => {
          next[input.dataset.setting] = Number(input.value);
        });
        if (Object.values(next).some((value) => !Number.isInteger(value))) {
          Y.toast('参数必须为整数');
          return;
        }
        if (next.maxPlayers < 1 || next.maxPlayers > 99 ||
          next.maxGuests < 0 || next.maxGuests > 99 ||
          next.maxWaitlist < 0 || next.maxWaitlist > 99) {
          Y.toast('报名参数不合法');
          return;
        }
        if (next.groupingManualWeight < 0 || next.groupingManualWeight > 100 ||
          next.groupingAutoWeight < 0 || next.groupingAutoWeight > 100 ||
          next.groupingManualWeight + next.groupingAutoWeight !== 100 ||
          next.groupingMinAppearances < 0 || next.groupingMinAppearances > 99 ||
          next.groupingAppearanceWeight < 0 || next.groupingAppearanceWeight > 100 ||
          next.groupingGoalWeight < 0 || next.groupingGoalWeight > 100 ||
          next.groupingAssistWeight < 0 || next.groupingAssistWeight > 100 ||
          next.groupingAppearanceWeight + next.groupingGoalWeight + next.groupingAssistWeight <= 0) {
          Y.toast('分组参数不合法');
          return;
        }
        Y.state.settings = { ...Y.state.settings, ...next };
        Y.save();
        Y.addAudit(Y.currentUser().name, '修改系统设置',
          `报名 ${next.maxPlayers}/${next.maxGuests}/候补 ${next.maxWaitlist}；分组 ${next.groupingManualWeight}/${next.groupingAutoWeight}，自动 ${next.groupingAppearanceWeight}/${next.groupingGoalWeight}/${next.groupingAssistWeight}，最少出场 ${next.groupingMinAppearances}`);
        Y.toast('设置已保存');
      });
    }

    /* ---------------- Audit ---------------- */
    function renderAudit(host) {
      const term = (value) => value.trim().toLowerCase();
      const filtered = Y.state.audit.filter((item) =>
        (!auditFilters.actor || String(item.actor || '').toLowerCase().includes(term(auditFilters.actor)))
        && (!auditFilters.action || String(item.action || '').toLowerCase().includes(term(auditFilters.action)))
        && (!auditFilters.target || String(item.target || '').toLowerCase().includes(term(auditFilters.target))));
      const sorted = filtered.slice().sort((a, b) => new Date(b.at) - new Date(a.at));
      const paged = Y.paginate(sorted, auditPage, auditPageSize);
      auditPage = paged.page;
      host.innerHTML = `<div class="admin-section-head">
        <div><h1>操作日志</h1><p class="muted">关键操作留痕，按时间倒序。</p></div>
        <span class="badge badge-soon">演示数据</span>
      </div>
      <form class="schedule-filters" data-audit-filter-form>
        <label class="filter-field"><span>操作人</span><input class="input input-sm" data-audit-filter="actor" placeholder="姓名或用户ID" value="${esc(auditFilters.actor)}"></label>
        <label class="filter-field"><span>动作</span><input class="input input-sm" data-audit-filter="action" placeholder="如 变更比赛状态" value="${esc(auditFilters.action)}"></label>
        <label class="filter-field grow"><span>对象</span><input class="input input-sm" data-audit-filter="target" placeholder="对象名称或ID" value="${esc(auditFilters.target)}"></label>
        <button class="btn btn-primary btn-sm" type="submit">搜索</button>
        <button class="btn btn-ghost btn-sm" type="button" data-audit-reset>重置</button>
      </form>
      <div class="audit-list">${paged.items.map((item) => `<div class="audit-row">
        <span class="actor">${esc(item.actor)}</span>
        <span class="action">${esc(item.action)}</span>
        <span class="target">${esc(item.target)}</span>
        <span class="time">${Y.formatDateTime(item.at)}</span>
        ${item.before || item.after ? `<details class="audit-detail"><summary>变更详情</summary><pre>${esc(JSON.stringify({ before: item.before, after: item.after }, null, 2))}</pre></details>` : ''}
      </div>`).join('') || '<div class="empty-inline">暂无日志</div>'}</div>
      ${Y.paginationHtml(paged.page, paged.totalPages, paged.total, auditPageSize)}`;
      $('[data-audit-filter-form]', host).addEventListener('submit', (event) => {
        event.preventDefault();
        $$('[data-audit-filter]', host).forEach((control) => {
          auditFilters[control.dataset.auditFilter] = control.value;
        });
        auditPage = 1;
        render();
      });
      $('[data-audit-reset]', host).addEventListener('click', () => {
        auditFilters = { actor: '', action: '', target: '' };
        auditPage = 1;
        render();
      });
      $$('[data-page]', host).forEach((button) => {
        button.addEventListener('click', () => {
          auditPage = Number(button.dataset.page);
          render();
        });
      });
      $('[data-page-size]', host).addEventListener('change', (event) => {
        auditPageSize = Number(event.target.value);
        auditPage = 1;
        render();
      });
    }

    render();
  }
})();
