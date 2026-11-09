(function () {
  const Y = window.YHFC;
  const $ = (selector, root) => (root || document).querySelector(selector);
  const $$ = (selector, root) => Array.from((root || document).querySelectorAll(selector));
  const esc = Y.escapeHtml;

  document.addEventListener('DOMContentLoaded', () => {
    const page = document.body.dataset.page;
    if (page === 'home') initHome();
    if (page === 'matches') initMatches();
    if (page === 'match') initMatch();
    if (page === 'team') initTeam();
    if (page === 'login') initLogin();
    if (page === 'profile') initProfile();
    if (page === 'invite') initInvite();
  });

  function initHome() {
    const host = $('#home-content');
    if (!host) return;
    const upcoming = Y.state.matches
      .filter((match) => ['SIGNUP_OPEN', 'SIGNUP_CLOSED', 'LOCKED', 'IN_PROGRESS', 'POSTPONED'].includes(match.status))
      .sort((a, b) => new Date(a.start) - new Date(b.start));
    const next = Y.nextMatch();
    const finished = Y.state.matches.filter((match) => match.status === 'FINISHED');
    if (!next) {
      host.innerHTML = `<section class="container section"><div class="empty-state card"><h2>暂无待进行比赛</h2><p class="muted">当前没有可报名或进行中的比赛。</p><a class="btn btn-primary" href="matches.html">查看赛程</a></div></section>`;
      return;
    }
    const nextConfirmed = Y.confirmed(next, 'PLAYER').length;
    const nextCapacity = next.capacity;
    const nextGuestConfirmed = Y.confirmed(next, 'GUEST').length;
    const nextWaitlist = Y.waitlisted(next, 'PLAYER').length;
    const nextWaitlistCapacity = next.waitlistCapacity ?? Y.state.settings.maxWaitlist;
    const names = Y.teamNames(next);
    const user = Y.currentUser();
    const registration = Y.registrationOf(next, user.id);
    const signupGate = Y.signupGate(next, user);
    const myType = Y.identityType(user);
    const formalFull = nextConfirmed >= nextCapacity;
    const guestFull = nextGuestConfirmed >= next.guestCapacity;
    const waitlistFull = nextWaitlist >= nextWaitlistCapacity;
    const capacityCta = myType === 'GUEST'
      ? (guestFull ? '外挂名额已满' : '立即报名')
      : (formalFull ? (waitlistFull ? '名额与候补已满' : '名额已满，加入候补') : '立即报名');
    const cta = Y.isVisitor() ? '登录后报名' : Y.isPending() ? '账号待审核' : registration ? '已报名 / 查看' : signupGate.ok ? capacityCta : signupGate.message;
    const ctaDisabled = Boolean(user.id && !registration && (!signupGate.ok || (myType === 'GUEST' ? guestFull : (formalFull && waitlistFull))));

    host.innerHTML = `<section class="hero">
      <div class="container hero-grid">
        <div class="hero-copy">
          <span class="eyebrow">YUNHE FC · 2026</span>
          <h1>云河FC</h1>
          <p>一支球队的运营台，也是一群人的比赛日记。</p>
          <div class="hero-actions">
            <a class="btn btn-primary" href="matches.html">查看赛程</a>
            <button class="btn btn-ghost" data-signup-next${ctaDisabled ? ' disabled' : ''}>${cta}</button>
          </div>
          <p class="hero-note">当前身份：${Y.roleLabel(Y.userRole())} · 数据保存在本地浏览器</p>
        </div>
        <div class="hero-match card">
          <div class="hero-match-head">
            <span>下一场</span>
            <span class="hero-match-tags">
              <span class="badge badge-type">${Y.typeLabel(next.type)}</span>
              <span class="badge ${Y.statusClass(next.status)}">${Y.statusLabel(next.status)}</span>
            </span>
          </div>
          <div class="hero-teams">
            <span>${esc(names.home)}</span>
            <span class="hero-score">${Y.scoreText(next)}</span>
            <span>${esc(names.away)}</span>
          </div>
          <div class="hero-meta">${Y.formatDateTime(next.start)} · ${esc(next.venue)}</div>
          ${Y.matchOrdinalLabel(next) ? `<div class="hero-ordinal">${Y.matchOrdinalLabel(next)}</div>` : ''}
          <div class="hero-progress">
            <div class="between"><span>报名队列</span><b>${nextConfirmed}/${nextCapacity}</b></div>
            <div class="progress"><i style="width:${Math.min(100, (nextConfirmed / Math.max(1, nextCapacity)) * 100)}%"></i></div>
          </div>
          <div class="hero-progress">
            <div class="between"><span>外挂队列</span><b>${nextGuestConfirmed}/${next.guestCapacity}</b></div>
            <div class="progress"><i style="width:${Math.min(100, (nextGuestConfirmed / Math.max(1, next.guestCapacity)) * 100)}%"></i></div>
          </div>
          <div class="hero-progress">
            <div class="between"><span>候补队列</span><b>${nextWaitlist}/${nextWaitlistCapacity}</b></div>
            <div class="progress"><i style="width:${Math.min(100, (nextWaitlist / Math.max(1, nextWaitlistCapacity)) * 100)}%"></i></div>
          </div>
          <a class="btn btn-ghost btn-block" href="match.html?id=${next.id}">查看比赛详情</a>
        </div>
      </div>
    </section>
    <section class="container section">
      <div class="section-head"><div><h2>近期赛程</h2><p class="muted">已结束 ${finished.length} 场 · 报名中 ${upcoming.filter((match) => match.status === 'SIGNUP_OPEN').length} 场</p></div><a class="link" href="matches.html">全部赛程 →</a></div>
      <div class="match-grid">${upcoming.slice(0, 3).map(Y.renderMatchCard).join('')}</div>
    </section>
    <section class="container section">
      <div class="section-head"><div><h2>球队火力</h2><p class="muted">二期能力占位，首版不展示数据</p></div><a class="link" href="team.html">查看球队 →</a></div>
      <div class="card"><div class="empty-inline">球队火力将在二期上线。</div></div>
    </section>
    <section class="container section">
      <div class="section-head"><div><h2>二期能力</h2><p class="muted">首版先把比赛和报名跑通</p></div><span class="badge badge-soon">规划中</span></div>
      <div class="feature-grid">
        ${[
          ['📈', '排行榜与身价', '年度出场、进球、助攻、胜率、缺勤与 AI 身价'],
          ['💰', '球队账本', '收入/支出分类，按月/年统计'],
          ['📸', '动态与相册', '比赛照片、B 站视频、点赞留言'],
          ['🗳️', '投票', '标题、图片选项、多选、截止时间'],
          ['📣', '公众号提醒', '赛事发布与模板消息'],
          ['🏆', '资产与荣誉', '器材台账、球队里程碑']
        ].map(([icon, title, text]) => `<div class="feature-card card"><span class="feature-icon">${icon}</span><h3>${title}</h3><p>${text}</p><span class="badge badge-soon">二期</span></div>`).join('')}
      </div>
    </section>`;

    const button = $('[data-signup-next]', host);
    if (button) {
      button.addEventListener('click', () => {
        const user = Y.currentUser();
        if (!user.id) {
          location.href = 'login.html?redirect=' + encodeURIComponent('index.html');
          return;
        }
        if (user.status !== 'ACTIVE') {
          Y.toast('账号待审核，暂不能报名');
          return;
        }
        const existingRegistration = Y.registrationOf(next, user.id);
        if (existingRegistration) {
          location.href = 'match.html?id=' + next.id;
          return;
        }
        const result = Y.signup(next);
        Y.toast(result.ok ? (result.status === 'CONFIRMED' ? '报名成功' : '名额已满，已进入候补') : result.message);
        if (result.ok) setTimeout(() => location.reload(), 600);
      });
    }
  }

  function sortMatches(a, b) {
    return new Date(b.start) - new Date(a.start);
  }

  function initMatches() {
    const host = $('#matches-content');
    if (!host) return;
    const filters = { type: 'ALL', venue: '', status: 'ALL', year: 'ALL', month: 'ALL' };
    let page = 1;
    let pageSize = 10;
    const types = Y.MATCH_TYPES.map((value) => [value, Y.typeLabel(value)]);
    const statuses = Y.MATCH_STATUSES;
    const years = [...new Set(Y.state.matches.map((match) => new Date(match.start).getFullYear()))].sort((a, b) => b - a);

    host.innerHTML = `<div class="container section">
      <div class="page-head">
        <div><span class="eyebrow">MATCHDAY</span><h1>赛程</h1><p class="muted">按比赛类型、地点、状态、年份、月份筛选；胜队名称以红色高亮</p></div>
      </div>
      <form class="schedule-filters" data-filter-form>
        <label class="filter-field"><span>比赛类型</span><select class="input input-sm" data-filter="type"><option value="ALL">全部</option>${types.map(([value, label]) => `<option value="${value}">${label}</option>`).join('')}</select></label>
        <label class="filter-field grow"><span>地点</span><input class="input input-sm" data-filter="venue" placeholder="搜索比赛地点"></label>
        <label class="filter-field"><span>比赛状态</span><select class="input input-sm" data-filter="status"><option value="ALL">全部</option>${statuses.map((value) => `<option value="${value}">${Y.statusLabel(value)}</option>`).join('')}</select></label>
        <label class="filter-field"><span>年份</span><select class="input input-sm" data-filter="year"><option value="ALL">全部</option>${years.map((year) => `<option value="${year}">${year}年</option>`).join('')}</select></label>
        <label class="filter-field"><span>月份</span><select class="input input-sm" data-filter="month"><option value="ALL">全部</option>${Array.from({ length: 12 }, (_, index) => `<option value="${index + 1}">${index + 1}月</option>`).join('')}</select></label>
        <button class="btn btn-primary btn-sm" type="submit" data-search-filters>搜索</button>
        <button class="btn btn-ghost btn-sm" type="button" data-reset-filters>重置</button>
      </form>
      <div class="match-grid" data-match-grid></div>
      <div class="empty-state" data-empty hidden><p>没有符合条件的比赛</p></div>
      <div data-pagination></div>
    </div>`;

    function filtered() {
      const term = filters.venue.trim().toLowerCase();
      return Y.state.matches.filter((match) => {
        const date = new Date(match.start);
        if (filters.type !== 'ALL' && match.type !== filters.type) return false;
        if (filters.status !== 'ALL' && match.status !== filters.status) return false;
        if (filters.year !== 'ALL' && date.getFullYear() !== Number(filters.year)) return false;
        if (filters.month !== 'ALL' && date.getMonth() + 1 !== Number(filters.month)) return false;
        if (term && !(match.venue || '').toLowerCase().includes(term)) return false;
        return true;
      }).sort(sortMatches);
    }

    function paint() {
      const list = filtered();
      const paged = Y.paginate(list, page, pageSize);
      page = paged.page;
      $('[data-match-grid]', host).innerHTML = paged.items.map(Y.renderMatchCard).join('');
      $('[data-empty]', host).hidden = list.length > 0;
      const pager = $('[data-pagination]', host);
      pager.innerHTML = Y.paginationHtml(paged.page, paged.totalPages, paged.total, pageSize);
      $$('[data-page]', pager).forEach((button) => {
        button.addEventListener('click', () => {
          page = Number(button.dataset.page);
          paint();
        });
      });
      $('[data-page-size]', pager).addEventListener('change', (event) => {
        pageSize = Number(event.target.value);
        page = 1;
        paint();
      });
    }

    function sync() {
      Object.keys(filters).forEach((key) => {
        $(`[data-filter="${key}"]`, host).value = filters[key];
      });
      paint();
    }

    $('[data-filter-form]', host).addEventListener('submit', (event) => {
      event.preventDefault();
      Object.keys(filters).forEach((key) => {
        filters[key] = $(`[data-filter="${key}"]`, host).value;
      });
      page = 1;
      paint();
    });
    $('[data-reset-filters]', host).addEventListener('click', () => {
      Object.assign(filters, { type: 'ALL', venue: '', status: 'ALL', year: 'ALL', month: 'ALL' });
      page = 1;
      sync();
    });
    paint();
  }

  function initMatch() {
    const host = $('#match-content');
    if (!host) return;
    const id = new URLSearchParams(location.search).get('id');
    const match = (id ? Y.matchById(id) : Y.state.matches[0]);
    if (!match) {
      host.innerHTML = '<div class="container section"><div class="empty-state"><h2>比赛不存在</h2></div></div>';
      return;
    }
    let tab = 'overview';
    let lineupSide = Y.ourLineupSide(match);

    function detailScoreText() {
      if (Y.activeEvents(match).length) {
        const derived = Y.derivedScore(match);
        return `${derived.home} - ${derived.away}`;
      }
      return Y.scoreText(match);
    }

    function tabs() {
      const items = [
        { key: 'overview', label: '概览' },
        { key: 'registration', label: '报名' },
        { key: 'events', label: '事件' }
      ];
      if (Y.hasPermission('lineup:view:any')) items.push({ key: 'lineup', label: '阵容' });
      if (Y.hasPermission('grouping:view:any') && Y.INTERNAL_TYPES.includes(match.type)) {
        items.push({ key: 'grouping', label: '分组' });
      }
      return items;
    }

    function render() {
      const items = tabs();
      if (!items.some((item) => item.key === tab)) tab = items[0].key;
      const names = Y.teamNames(match);
      const winner = Y.winnerName(match);
      const result = Y.resultOf(match);
      const myInviteRegistration = Y.registrationOf(match, Y.currentUser().id);
      const canInviteTeammate = Y.hasPermission('invite:create:match')
        && ['SIGNUP_OPEN', 'SIGNUP_CLOSED'].includes(match.status)
        && (Y.hasPermission('match:edit:any') || myInviteRegistration?.status === 'CONFIRMED');
      host.innerHTML = `<div class="container section">
        <div class="match-hero">
          <div class="match-hero-top">
            <span class="badge badge-type">${Y.typeLabel(match.type)}</span>
            <span class="badge ${Y.statusClass(match.status)}">${Y.statusLabel(match.status)}</span>
            ${result.label !== '—' ? `<span class="result ${result.cls}">${result.label}</span>` : ''}
          </div>
          <div class="match-scoreboard">
            <div class="score-team ${winner === names.home ? 'winner' : ''}">${esc(names.home)}</div>
            <div class="score-number">${detailScoreText()}</div>
            <div class="score-team ${winner === names.away ? 'winner' : ''}">${esc(names.away)}</div>
          </div>
          <div class="match-meta-grid">
            <span>📅 ${Y.formatDateTime(match.start)}</span>
            <span>📍 ${esc(match.venue)}</span>
            <span>⏱ ${Y.formatTime(match.start)}–${Y.formatTime(match.end)}</span>
            <span>🏆 ${esc(match.competition || '—')}${match.round ? ` · 第 ${match.round} 轮` : ''}</span>
            ${Y.matchOrdinalLabel(match) ? `<span>🏁 ${Y.matchOrdinalLabel(match)}</span>` : ''}
          </div>
          <div class="match-hero-actions">
            <button class="btn btn-ghost btn-sm" data-action="share">分享比赛</button>
            ${canInviteTeammate ? '<button class="btn btn-ghost btn-sm" data-action="invite-teammate">邀请队友</button>' : ''}
            ${Y.hasPermission('match:status:any') ? `<select class="input input-sm" data-action="c-status" aria-label="修改比赛状态">${Y.statusOptions(match).map((option) => `<option value="${option.status}" ${match.status === option.status ? 'selected' : ''} ${option.disabled ? `disabled title="${option.reason}"` : ''}>${Y.statusLabel(option.status)}</option>`).join('')}</select>` : ''}
          </div>
        </div>
        <div class="tabs">${items.map((item) => `<button class="tab ${item.key === tab ? 'active' : ''}" data-tab="${item.key}">${item.label}</button>`).join('')}</div>
        <div class="tab-panel">${renderTab(tab)}</div>
      </div>`;
      bind();
    }

    function renderTab(key) {
      if (key === 'overview') return renderOverview();
      if (key === 'registration') return renderRegistration();
      if (key === 'events') return renderEvents();
      if (key === 'lineup') return renderLineup();
      if (key === 'grouping') return renderGrouping();
      return '';
    }

    function renderOverview() {
      const formalConfirmed = Y.confirmed(match, 'PLAYER').length;
      const guestConfirmed = Y.confirmed(match, 'GUEST').length;
      const totalConfirmed = formalConfirmed + guestConfirmed;
      const totalCapacity = match.capacity + match.guestCapacity;
      const totalWaitlist = Y.waitlisted(match, 'PLAYER').length;
      const waitlistCapacity = match.waitlistCapacity ?? Y.state.settings.maxWaitlist;
      const voided = match.status === 'CANCELLED';
      return `<div class="overview-grid overview-grid-single">
        <section class="card">
          <div class="card-head"><h2>报名概况</h2><span class="badge ${Y.statusClass(match.status)}">${Y.statusLabel(match.status)}</span></div>
          ${voided ? `<div class="banner banner-warn">${Y.CANCELLED_REGISTRATION_NOTICE}</div>` : ''}
          ${voided ? '' : `<div class="progress-row"><div class="between"><span>正式球员</span><b>${formalConfirmed}/${match.capacity}</b></div><div class="progress"><i style="width:${Math.min(100, (formalConfirmed / Math.max(1, match.capacity)) * 100)}%"></i></div></div>
          <div class="progress-row"><div class="between"><span>外挂球员</span><b>${guestConfirmed}/${match.guestCapacity}</b></div><div class="progress"><i style="width:${Math.min(100, (guestConfirmed / Math.max(1, match.guestCapacity)) * 100)}%"></i></div></div>`}
          <div class="meta-list">
            <div><span>${voided ? '历史报名' : '总报名'}</span><b>${voided ? `${totalConfirmed} 条` : `${totalConfirmed}/${totalCapacity}`}</b></div>
            <div><span>${voided ? '历史候补' : '候补人数'}</span><b>${voided ? `${totalWaitlist} 条` : `${totalWaitlist}/${waitlistCapacity}`}</b></div>
            <div><span>报名截止</span><b>${Y.formatDateTime(match.signupDeadline)}</b></div>
            <div><span>锁定</span><b>${Y.formatDateTime(match.cancelLock)}</b></div>
          </div>
        </section>
      </div>`;
    }

    function renderRegistration() {
      const user = Y.currentUser();
      if (!user.id) {
        return `<div class="empty-state card"><h2>登录后报名</h2><p>公开访客可以查看比赛信息，报名需要先登录。</p><a class="btn btn-primary" href="login.html?redirect=${encodeURIComponent('match.html?id=' + match.id)}">去登录</a></div>`;
      }
      if (user.status !== 'ACTIVE') {
        return `<div class="banner banner-warn">账号待审核：可以浏览比赛，暂不能报名或签到。</div>`;
      }
      const registration = Y.registrationOf(match, user.id);
      const voided = match.status === 'CANCELLED';
      const postponed = match.status === 'POSTPONED';
      const attendance = Y.attendanceOf(match, user.id);
      const formalConfirmed = Y.confirmed(match, 'PLAYER');
      const formalWaitlist = Y.waitlisted(match, 'PLAYER');
      const guestConfirmed = Y.confirmed(match, 'GUEST');
      const waitlistCapacity = match.waitlistCapacity ?? Y.state.settings.maxWaitlist;
      const totalWaitlist = formalWaitlist.length;
      const formalFull = formalConfirmed.length >= match.capacity;
      const guestFull = guestConfirmed.length >= match.guestCapacity;
      const waitlistFull = totalWaitlist >= waitlistCapacity;
      const myType = Y.identityType(user);
      const gate = Y.signupGate(match, user);
      const capacityLabel = myType === 'GUEST'
        ? (guestFull ? '外挂名额已满' : '立即报名')
        : (formalFull ? (waitlistFull ? '名额与候补已满' : '名额已满，加入候补') : '立即报名');
      const signupLabel = gate.ok ? capacityLabel : gate.message;
      const signupDisabled = !registration && (!gate.ok || (myType === 'GUEST' ? guestFull : (formalFull && waitlistFull)));
      const cancelGate = registration ? Y.cancelGate(match, registration) : { ok: false, message: '' };
      const canShowList = Y.hasPermission('attendance:view:any');
      const myBadge = registration ? Y.registrationBadgeMeta(match, registration) : { label: '未报名', cls: 'badge-muted' };
      const tempPresent = canShowList && !['CANCELLED', 'POSTPONED'].includes(match.status)
        ? (match.attendance || []).filter((item) => item.status === 'TEMP_PRESENT' &&
          ![...formalConfirmed, ...guestConfirmed].some((reg) => Number(reg.playerId) === Number(item.playerId)))
        : [];
      return `<div class="registration-layout">
        ${postponed ? '<div class="banner banner-warn">比赛已延期，等待管理员重新排期；报名记录保留。</div>' : ''}
        <section class="card">
          <div class="card-head"><h2>我的报名</h2><span class="badge ${myBadge.cls}">${myBadge.label}</span></div>
          ${voided ? '' : `<div class="progress-row"><div class="between"><span>正式名额</span><b>${formalConfirmed.length}/${match.capacity}</b></div><div class="progress"><i style="width:${Math.min(100, (formalConfirmed.length / Math.max(1, match.capacity)) * 100)}%"></i></div></div>
          <div class="progress-row"><div class="between"><span>外挂名额</span><b>${guestConfirmed.length}/${match.guestCapacity}</b></div><div class="progress"><i style="width:${Math.min(100, (guestConfirmed.length / Math.max(1, match.guestCapacity)) * 100)}%"></i></div></div>
          <div class="progress-row"><div class="between"><span>候补</span><b>${totalWaitlist}/${waitlistCapacity}</b></div><div class="progress"><i style="width:${Math.min(100, (totalWaitlist / Math.max(1, waitlistCapacity)) * 100)}%"></i></div></div>`}
          <div class="registration-actions">
            ${registration
              ? (voided
                ? '<p class="muted small">比赛已取消，报名作废</p>'
                : `<button class="btn btn-ghost btn-block" data-action="cancel" ${cancelGate.ok ? '' : 'disabled'}>${registration.status === 'CONFIRMED' ? '取消报名' : '取消候补'}</button>
                 ${cancelGate.ok ? '' : `<p class="muted small">${cancelGate.message}</p>`}`)
              : `<button class="btn btn-primary btn-block" data-action="signup" ${signupDisabled ? 'disabled' : ''}>${signupLabel}</button>`}
          </div>
          ${registration && registration.status === 'CONFIRMED' && !['CANCELLED', 'POSTPONED'].includes(match.status)
            ? `<div class="checkin-box">
                <div><strong>签到</strong><p class="muted small">窗口：开赛前 4 小时至比赛当天 23:59</p></div>
                ${attendance
                  ? `<span class="badge ${attendance.status === 'PRESENT' ? 'badge-open' : 'badge-cancelled'}">${attendance.status === 'PRESENT' ? '已签到' : '已标记'}</span>`
                  : `<button class="btn btn-primary btn-sm" data-action="checkin" ${Y.checkInWindow(match).ok ? '' : 'disabled'}>${Y.checkInWindow(match).ok ? '签到' : '不在签到时间'}</button>`}
              </div>` : ''}
          <p class="muted small">报名截止：${Y.formatDateTime(match.signupDeadline)} · 锁定：${Y.formatDateTime(match.cancelLock)}</p>
        </section>
        <section class="card">
          <div class="card-head"><h2>报名名单</h2><span class="muted">${canShowList ? '正式球员/管理员可见' : '仅显示自己的状态'}</span>${Y.hasPermission('registration:remove:any') ? `<a class="link small" href="admin.html?section=registrations&match=${match.id}">去后台管理 →</a>` : ''}</div>
          ${canShowList
            ? `<div class="pool-title"><span>正式球员</span><span>${formalConfirmed.length} 确认 · ${formalWaitlist.length} 候补</span></div>
               ${formalConfirmed.map((reg) => registrationRow(reg)).join('') || '<div class="empty-inline">暂无正式球员报名</div>'}
               ${formalWaitlist.map((reg) => registrationRow(reg, true)).join('')}
               <div class="pool-title"><span>外挂球员</span><span>${guestConfirmed.length} 确认 · 不设候补</span></div>
               ${guestConfirmed.map((reg) => registrationRow(reg)).join('') || '<div class="empty-inline">暂无外挂球员报名</div>'}`
            : '<div class="empty-inline">报名名单仅正式球员和管理员可见。</div>'}
        </section>
        ${!['CANCELLED', 'POSTPONED'].includes(match.status) && (canShowList || registration) ? `<section class="card">
          <div class="card-head"><h2>考勤</h2><span class="muted">${canShowList ? '正式球员/管理员可见' : '仅显示本人'}</span></div>
          ${(canShowList ? [...formalConfirmed, ...guestConfirmed] : [registration]).map((reg) => {
            const player = Y.playerById(reg.playerId);
            const attendance = Y.attendanceOf(match, reg.playerId);
            const label = attendance
              ? (Y.ATTENDANCE_STATUS_META[attendance.status] || {}).label || attendance.status
              : '未记录';
            return `<div class="registration-row">${Y.avatar(player, 'sm')}<div class="registration-main"><strong>${esc(player?.name || '未知球员')}</strong><small>#${player?.jersey ?? '—'} · ${Y.positionLabel(player?.position)}</small></div><span class="badge ${attendance && attendance.status === 'PRESENT' ? 'badge-open' : attendance && attendance.status === 'ABSENT' ? 'badge-cancelled' : 'badge-muted'}">${label}</span></div>`;
          }).join('') || (tempPresent.length ? '' : '<div class="empty-inline">暂无考勤记录</div>')}
          ${tempPresent.map((item) => {
            const player = Y.playerById(item.playerId);
            return `<div class="registration-row">${Y.avatar(player, 'sm')}<div class="registration-main"><strong>${esc(player?.name || '未知球员')}</strong><small>#${player?.jersey ?? '—'} · ${Y.positionLabel(player?.position)}</small></div><span class="badge badge-postponed">临时到场</span></div>`;
          }).join('')}
        </section>` : ''}
      </div>`;
    }

    function registrationRow(reg, waitlist) {
      const player = Y.playerById(reg.playerId);
      const badge = Y.registrationBadgeMeta(match, reg);
      return `<div class="registration-row">
        ${Y.avatar(player, 'sm')}
        <div class="registration-main"><strong>${esc(player?.name || '未知球员')}</strong><small>#${player?.jersey ?? '—'} · ${Y.positionLabel(player?.position)}</small></div>
        <span class="badge ${badge.cls}">${badge.label}</span>
      </div>`;
    }

    function renderEvents() {
      const events = Y.activeEvents(match).slice().sort(Y.compareEvents);
      const goals = events.filter((event) => event.type === 'GOAL').length;
      const cards = events.filter((event) => event.type.includes('CARD')).length;
      const subs = events.filter((event) => event.type === 'SUBSTITUTION').length;
      const hasEventPermission = Y.hasPermission('event:create:own') || Y.hasPermission('event:edit:any');
      const eventOpen = Y.eventEntryOpen(match);
      const withinWindow = Y.hasPermission('event:edit:any') || Date.now() <= Y.eventEditWindowEndsAt(match);
      const canAdd = hasEventPermission && eventOpen && withinWindow;
      const eventDisabledReason = !eventOpen
        ? (['CANCELLED', 'POSTPONED'].includes(match.status) ? '已取消/已延期的比赛不可录入事件' : '仅进行中或已结束的比赛可录入事件')
        : (!withinWindow ? '已超过赛后 24 小时可编辑窗口' : '');
      const eventActionHidden = ['CANCELLED', 'POSTPONED'].includes(match.status);
      const addEventAction = eventActionHidden || !hasEventPermission ? '' : (canAdd
        ? '<button class="btn btn-primary btn-sm" data-action="add-event">录入事件</button>'
        : `<button class="btn btn-primary btn-sm" disabled title="${eventDisabledReason}">录入事件</button>`);
      const actions = `${addEventAction}${Y.canAccessAdmin() ? `<a class="btn btn-ghost btn-sm" href="admin.html?section=events&match=${match.id}">去管理后台</a>` : ''}`;
      return `<div class="events-tab">
        <section class="card">
          <div class="card-head"><h2>比赛事件</h2><span class="muted">${events.length} 条</span></div>
          <div class="stat-grid">
            <div><b>${goals}</b><span>进球</span></div>
            <div><b>${cards}</b><span>牌</span></div>
            <div><b>${subs}</b><span>换人</span></div>
          </div>
          ${match.status === 'FINISHED' ? `<p class="muted small">比分 ${detailScoreText()}</p>` : ''}
        </section>
        <section class="card">
          ${actions ? `<div class="event-actions">${actions}</div>` : ''}
          ${events.length ? Y.renderEventTimeline(match, events, {
            admin: false,
            canEdit: (event) => !['CANCELLED', 'POSTPONED'].includes(match.status) && Y.canEditEvent(match, event, Y.currentUser())
          }) : '<div class="empty-inline">还没有事件记录</div>'}
        </section>
      </div>`;
    }

    function renderLineup() {
      const sides = ['HOME', 'AWAY'];
      const side = sides.includes(lineupSide) ? lineupSide : Y.ourLineupSide(match);
      const lineup = (match.lineups || {})[side];
      const sideNames = Y.teamNames(match);
      const opponentLineup = Y.isOpponentLineup(match, side);
      return `<div class="card">
        <div class="card-head"><h2>阵容</h2>${Y.hasPermission('lineup:edit:any') ? `<a class="link" href="admin.html?section=lineup&match=${match.id}&side=${side}">去编辑 →</a>` : '<span class="muted">仅管理员可编辑</span>'}</div>
        <div class="chip-row">${sides.map((item) => `<button class="chip ${item === side ? 'active' : ''}" data-public-side="${item}">${item === 'HOME' ? sideNames.home : sideNames.away}</button>`).join('')}</div>
        ${lineup
          ? `<p class="muted">${Y.currentFormation(lineup).name} · ${opponentLineup ? '对手方仅记录阵型与占位槽位，不录入球员姓名' : `首发 ${Object.keys(lineup.starters || {}).length}/8`}</p>${Y.pitchHtml(lineup, opponentLineup ? {} : Y.playerMap(), false)}${opponentLineup ? '' : `<div class="bench"><strong>替补</strong>${(lineup.bench || []).map((id) => playerChip(Y.playerById(id))).join('') || '<span class="muted">暂无替补</span>'}</div>`}`
          : '<div class="empty-inline">尚未排阵容</div>'}
      </div>`;
    }

    function renderGrouping() {
      if (!Y.INTERNAL_TYPES.includes(match.type)) {
        return `<div class="empty-state card"><h2>仅队内赛/训练赛支持分组</h2><p>对外比赛只有主队我方阵容，客队仅记录阵型。</p></div>`;
      }
      const grouping = match.grouping;
      const summary = grouping ? Y.groupingSummary(match, grouping) : null;
      const rosterDiff = summary ? summary.rosterDiff : { changed: false, addedIds: [], removedIds: [] };
      const warnings = grouping ? Y.combinedWarnings(grouping, summary) : [];
      const nameOf = (id) => Y.playerById(id)?.name || `#${id}`;
      const weights = grouping && grouping.weights
        ? `手动 ${Math.round(grouping.weights.manualWeight * 100)}% + 自动 ${Math.round(grouping.weights.autoWeight * 100)}%`
        : '';
      return `<div class="card">
        <div class="card-head"><h2>队内分组</h2>${Y.hasPermission('grouping:generate:any') ? `<a class="link" href="admin.html?section=grouping&match=${match.id}">去生成/调整 →</a>` : '<span class="muted">管理员生成，球员只读</span>'}</div>
        ${grouping
          ? `${rosterDiff.changed ? `<div class="banner banner-warn">名单已变化，建议重新生成。新增：${rosterDiff.addedIds.map(nameOf).join('、') || '无'}；移除：${rosterDiff.removedIds.map(nameOf).join('、') || '无'}。</div>` : ''}
             ${warnings.length ? `<div class="banner banner-warn">${warnings.map(esc).join('；')}</div>` : ''}
             <div class="group-grid">${['A', 'B'].map((team) => groupColumn(team, grouping[team] || [], summary, grouping)).join('')}</div>
             <p class="muted small">总分差 ${summary.diff.toFixed(2)}${weights ? ` · ${weights}` : ''}${grouping.savedAt ? ` · 保存于 ${Y.formatDateTime(grouping.savedAt)}` : ''}${grouping.snapshot ? ' · 已保存快照' : ''}</p>`
          : '<div class="empty-inline">尚未生成分组</div>'}
      </div>`;
    }

    function groupColumn(team, ids, summary, grouping) {
      const players = ids.map((id) => Y.playerById(id)).filter(Boolean);
      const total = team === 'A' ? summary.totalA : summary.totalB;
      const pos = team === 'A' ? summary.posA : summary.posB;
      const posText = Y.GROUP_POSITIONS
        .map((key) => `${Y.positionLabel(key)} ${pos[key] || 0}`).join(' · ');
      const teamName = Y.groupingTeamName(match, team);
      const scoreOf = (id) => (grouping.scores && grouping.scores[id] ? Number(grouping.scores[id].final).toFixed(2) : '—');
      return `<div class="group-column"><div class="group-head"><strong>${teamName}</strong><span>${players.length} 人 · 综合 ${total.toFixed(2)}</span><small>${posText}</small></div>${players.map((player) => `<span class="player-chip">${Y.avatar(player, 'xs')}<span>${esc(player.name)}</span><small>#${player.jersey} · ${scoreOf(player.id)}</small></span>`).join('')}</div>`;
    }

    function playerChip(player) {
      if (!player) return '';
      return `<span class="player-chip">${Y.avatar(player, 'xs')}<span>${esc(player.name)}</span><small>#${player.jersey}</small></span>`;
    }

    function localDateTimeValue(date) {
      const value = new Date(date);
      const pad = (number) => String(number).padStart(2, '0');
      return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())}T${pad(value.getHours())}:${pad(value.getMinutes())}`;
    }

    function openCCancelModal() {
      const body = `<form id="c-cancel-form" class="form-grid">
        <label class="full">取消原因<textarea class="input" name="reason" rows="3" required placeholder="例如：场地临时关闭"></textarea></label>
        <p class="muted small full">取消后报名记录保留但作废，不计考勤。</p>
      </form>`;
      const modal = Y.openModal('取消比赛', body, '<button class="btn btn-danger" id="c-cancel-submit">确认取消</button><button class="btn btn-ghost" data-close-modal>取消</button>');
      modal.querySelector('#c-cancel-submit').addEventListener('click', () => {
        const form = modal.querySelector('#c-cancel-form');
        if (!form.reportValidity()) return;
        const reason = String(new FormData(form).get('reason') || '').trim();
        if (!reason) { Y.toast('请填写取消原因'); return; }
        const result = Y.cancelMatch(match, reason, Y.currentUser().name);
        if (!result.ok) { Y.toast(result.message); return; }
        Y.closeModal();
        Y.toast('比赛已取消');
        render();
      });
    }

    function openCPostponeModal() {
      const defaultStart = new Date(Math.max(Date.now() + 24 * 60 * 60 * 1000, new Date(match.start).getTime()));
      defaultStart.setHours(20, 0, 0, 0);
      const defaultEnd = new Date(defaultStart.getTime() + 2 * 60 * 60 * 1000);
      const body = `<form id="c-postpone-form" class="form-grid">
        <label>新开始时间<input class="input" type="datetime-local" name="start" value="${localDateTimeValue(defaultStart)}" required></label>
        <label>新结束时间<input class="input" type="datetime-local" name="end" value="${localDateTimeValue(defaultEnd)}" required></label>
        <p class="muted small full">报名记录保留；延期后状态为“已延期”，需在后台重新排期回报名中。</p>
      </form>`;
      const modal = Y.openModal('延期比赛', body, '<button class="btn btn-primary" id="c-postpone-submit">确认延期</button><button class="btn btn-ghost" data-close-modal>取消</button>');
      modal.querySelector('#c-postpone-submit').addEventListener('click', () => {
        const form = modal.querySelector('#c-postpone-form');
        if (!form.reportValidity()) return;
        const data = new FormData(form);
        const start = new Date(data.get('start'));
        const end = new Date(data.get('end'));
        if (start.getHours() * 60 + start.getMinutes() <= 10 * 60) { Y.toast('开赛时间必须晚于当天 10:00'); return; }
        if (!(start < end)) { Y.toast('结束时间必须晚于开始时间'); return; }
        const hours = (end - start) / (60 * 60 * 1000);
        if (hours < 2 || hours > 4) { Y.toast('开始时间与结束时间相差必须为 2–4 小时'); return; }
        const deadlines = Y.defaultDeadlines(start);
        const result = Y.postponeMatch(match, {
          start: start.toISOString(), end: end.toISOString(),
          signupDeadline: deadlines.signupDeadline, cancelLock: deadlines.cancelLock
        }, Y.currentUser().name);
        if (!result.ok) { Y.toast(result.message); return; }
        Y.closeModal();
        Y.toast('比赛已延期');
        render();
      });
    }

    function bind() {
      $$('[data-tab]', host).forEach((button) => {
        button.addEventListener('click', () => {
          tab = button.dataset.tab;
          render();
        });
      });
      const share = $('[data-action="share"]', host);
      if (share) {
        share.addEventListener('click', async () => {
          try {
            await navigator.clipboard.writeText(location.href);
            Y.toast('比赛链接已复制');
          } catch (error) {
            Y.toast('请手动复制地址栏链接');
          }
        });
      }
      $$('[data-public-side]', host).forEach((button) => {
        button.addEventListener('click', () => {
          lineupSide = button.dataset.publicSide;
          render();
        });
      });
      const cStatus = $('[data-action="c-status"]', host);
      if (cStatus) {
        cStatus.addEventListener('change', () => {
          const nextStatus = cStatus.value;
          cStatus.value = match.status;
          if (nextStatus === 'POSTPONED') { openCPostponeModal(); return; }
          if (nextStatus === 'CANCELLED') { openCCancelModal(); return; }
          const result = Y.setMatchStatus(match, nextStatus, Y.currentUser().name, { manual: true });
          Y.toast(result.ok ? '比赛状态已更新' : result.message);
          render();
        });
      }
      const inviteTeammate = $('[data-action="invite-teammate"]', host);
      if (inviteTeammate) {
        inviteTeammate.addEventListener('click', async () => {
          if (!Y.hasPermission('invite:create:match')) { Y.toast('没有创建比赛邀请的权限'); return; }
          const invite = {
            id: 'inv' + Date.now(),
            type: 'MATCH',
            matchId: match.id,
            inviter: Y.currentUser().name,
            expiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString(),
            maxUses: null,
            usedCount: 0,
            status: 'ACTIVE',
            token: Math.random().toString(36).slice(2) + Date.now().toString(36),
            createdAt: new Date().toISOString()
          };
          if (!Array.isArray(Y.state.invites)) Y.state.invites = [];
          Y.state.invites.push(invite);
          Y.save();
          Y.addAudit(Y.currentUser().name, '创建比赛邀请', `${match.title} / ${invite.id}`);
          const url = `invite.html?token=${encodeURIComponent(invite.token)}`;
          try { await navigator.clipboard.writeText(url); Y.toast('比赛邀请链接已复制'); }
          catch { Y.toast(url); }
        });
      }
      const signup = $('[data-action="signup"]', host);
      if (signup) {
        signup.addEventListener('click', () => {
          const result = Y.signup(match);
          Y.toast(result.ok ? (result.status === 'CONFIRMED' ? '报名成功' : '名额已满，已进入候补') : result.message);
          if (result.ok) render();
        });
      }
      const cancel = $('[data-action="cancel"]', host);
      if (cancel) {
        cancel.addEventListener('click', () => {
          const result = Y.cancelSignup(match, Y.currentUser().id);
          Y.toast(result.ok ? '已取消报名' : result.message);
          if (result.ok) render();
        });
      }
      const checkin = $('[data-action="checkin"]', host);
      if (checkin) {
        checkin.addEventListener('click', () => {
          const result = Y.checkIn(match, Y.currentUser().id);
          Y.toast(result.ok ? '签到成功' : result.message);
          if (result.ok) render();
        });
      }
      const addEvent = $('[data-action="add-event"]', host);
      if (addEvent) {
        addEvent.addEventListener('click', () => Y.openEventModal(match, null, { onSaved: render }));
      }
      $$('[data-edit-event]', host).forEach((button) => {
        button.addEventListener('click', () => {
          const event = Y.activeEvents(match).find((item) => item.id === button.dataset.editEvent);
          if (!event) return;
          if (!Y.canEditEvent(match, event, Y.currentUser())) {
            Y.toast('没有权限修改该事件或已超过可编辑时间');
            return;
          }
          Y.openEventModal(match, event, { onSaved: render });
        });
      });
    }

render();
  }

  function initTeam() {
    const host = $('#team-content');
    if (!host) return;
    let showAll = false;
    const formal = Y.state.players.filter((player) => player.type === 'PLAYER' && player.status === 'ACTIVE');
    const positionOrder = ['FORWARD', 'MIDFIELDER', 'DEFENDER', 'GOALKEEPER'];

    function render() {
      const groups = positionOrder.map((position) => ({
        position,
        label: Y.positionLabel(position),
        players: formal
          .filter((player) => player.position === position)
          .sort((a, b) => (Number(b.appearances) || 0) - (Number(a.appearances) || 0) || (Number(a.jersey) || 0) - (Number(b.jersey) || 0))
      })).filter((group) => group.players.length);
      const hasMore = groups.some((group) => group.players.length > 10);
      host.innerHTML = `<div class="container section">
        <div class="page-head">
          <div><span class="eyebrow">SQUAD</span><h1>球队</h1><p class="muted">${formal.length} 名正式球员 · 按位置分组 · 组内出场降序 → 号码升序</p></div>
          <span class="badge badge-open">本赛季数据 · 最多延迟 10 分钟</span>
        </div>
        ${groups.length ? groups.map((group) => {
          const list = showAll ? group.players : group.players.slice(0, 10);
          return `<section class="team-position">
            <div class="section-head"><div><h2>${group.label}</h2><p class="muted">${group.players.length} 人</p></div></div>
            <div class="player-grid">${list.map(playerCard).join('')}</div>
          </section>`;
        }).join('') : '<div class="empty-state"><p>暂无正式球员</p></div>'}
        ${hasMore ? `<div class="team-more"><button class="btn btn-ghost btn-sm" data-team-more>${showAll ? '收起' : '显示全部'}</button></div>` : ''}
        <p class="muted small">公开字段：姓名、号码、位置、地区、头像、星级；手机号、考勤、账本、战术不公开。外挂球员默认不进入球队页。</p>
      </div>`;
      const more = $('[data-team-more]', host);
      if (more) {
        more.addEventListener('click', () => {
          showAll = !showAll;
          render();
        });
      }
    }

    render();
  }

  function playerCard(player) {
    return `<article class="player-card">
      <div class="player-card-top">${Y.avatar(player, 'md')}<span class="jersey">#${player.jersey}</span></div>
      <h3>${esc(player.name)}</h3>
      <p class="muted">${esc(player.region || '—')} · ${Y.positionLabel(player.position)}</p>
      <div class="player-stars">${Y.stars(player.skill)}</div>
      <div class="player-stats">
        <span><b>${player.appearances}</b><small>出场</small></span>
        <span><b>${player.goals}</b><small>进球</small></span>
        <span><b>${player.assists}</b><small>助攻</small></span>
      </div>
    </article>`;
  }

  function initInvite() {
    const host = $('#invite-content');
    if (!host) return;
    const token = new URLSearchParams(location.search).get('token') || '';
    const invite = (Y.state.invites || []).find((item) => item.token === token);
    const invalid = !invite
      || invite.status !== 'ACTIVE'
      || new Date(invite.expiresAt).getTime() < Date.now()
      || (invite.maxUses != null && Number(invite.usedCount) >= Number(invite.maxUses));
    if (invalid) {
      host.innerHTML = `<section class="container section"><div class="card"><h1>邀请不可用</h1><p class="muted">链接不存在、已过期、已撤销或使用次数已用尽。</p><a class="btn btn-primary" href="index.html">返回首页</a></div></section>`;
      return;
    }
    const user = Y.currentUser();
    const match = invite.matchId ? Y.matchById(invite.matchId) : null;
    const isMember = user.id && user.type === 'PLAYER' && user.status === 'ACTIVE';
    const action = invite.type === 'TEAM'
      ? (isMember
        ? '<a class="btn btn-primary" href="index.html">已是成员，返回首页</a>'
        : `<button class="btn btn-primary" data-accept-invite${user.id ? '' : ' data-need-login'}>${user.id ? '接受邀请' : '登录后接受邀请'}</button>`)
      : (match
        ? `<a class="btn btn-primary" href="match.html?id=${match.id}">查看比赛并报名</a>`
        : '<span class="muted">关联比赛不存在</span>');
    host.innerHTML = `<section class="container section invite-landing">
      <div class="card">
        <span class="badge badge-type">${invite.type === 'TEAM' ? '入队邀请' : '比赛邀请'}</span>
        <h1>${invite.type === 'TEAM' ? '加入云河FC' : '云河FC 比赛邀请'}</h1>
        <p class="muted">邀请人：${esc(invite.inviter || '—')} · 有效期至 ${Y.formatDateTime(invite.expiresAt)}</p>
        ${invite.type === 'TEAM'
          ? '<p>接受后将直接加入球队（免审核）。已有账号也可使用此链接完成入队。</p>'
          : (match ? `<p>${esc(match.title)} · ${Y.formatDateTime(match.start)} · ${esc(match.venue)}</p><p class="muted">比赛邀请只引导报名，不改变账号状态。</p>` : '')}
        <div class="hero-actions">${action}</div>
      </div>
    </section>`;
    const accept = $('[data-accept-invite]', host);
    if (accept) accept.addEventListener('click', () => {
      if (!user.id) {
        location.href = 'login.html?redirect=' + encodeURIComponent(`invite.html?token=${encodeURIComponent(token)}`);
        return;
      }
      if (user.status === 'DISABLED') { Y.toast('注销账号不能接受邀请'); return; }
      user.status = 'ACTIVE';
      user.type = 'PLAYER';
      user.roles = (user.roles || []).filter((code) => code !== 'GUEST');
      if (!user.roles.includes('PLAYER')) user.roles.push('PLAYER');
      invite.usedCount = Number(invite.usedCount || 0) + 1;
      Y.save();
      Y.addAudit(user.name, '接受入队邀请', invite.id);
      Y.notify(user.id, '已加入球队', '你已通过邀请加入云河FC。');
      Y.toast('已加入球队');
      location.href = 'index.html';
    });
  }

  function initLogin() {
    const host = $('#login-content');
    if (!host) return;
    let mode = 'login';
    const requestedRedirect = new URLSearchParams(location.search).get('redirect') || 'profile.html';
    const redirect = /^[A-Za-z0-9._-]+\.html(?:\?[A-Za-z0-9._=&%-]*)?$/.test(requestedRedirect) ? requestedRedirect : 'profile.html';
    const demos = Y.DATA.demoUsers
      .filter((item) => item.id > 0)
      .map((item) => ({ id: item.id, label: item.label, desc: Y.roleLabel(item.role) }));

    function render() {
      host.innerHTML = `<div class="auth-shell">
        <div class="auth-card card">
          <div class="auth-head"><span class="brand-mark"><img src="assets/logo.png" alt="云河FC"></span><div><h1>云河FC</h1><p class="muted">登录或注册球队账号</p></div></div>
          <div class="tabs auth-tabs">
            <button class="tab ${mode === 'login' ? 'active' : ''}" data-mode="login">登录</button>
            <button class="tab ${mode === 'register' ? 'active' : ''}" data-mode="register">注册</button>
          </div>
          ${mode === 'login' ? loginForm() : registerForm()}
          <div class="demo-accounts">
            <p class="muted small">快速体验（不需要密码）</p>
            <div class="demo-grid">${demos.map((item) => `<button class="demo-account" data-demo-id="${item.id}"><strong>${item.label}</strong><small>${item.desc}</small></button>`).join('')}</div>
          </div>
          <p class="form-note">原型演示：验证码固定 123456，不会发送真实短信。</p>
        </div>
      </div>`;
      bind();
    }

    function loginForm() {
      return `<form class="form-stack" data-login-form>
        <label>手机号<input class="input" name="phone" placeholder="演示账号：13800000000" autocomplete="tel"></label>
        <label>密码<input class="input" type="password" name="password" placeholder="任意密码" autocomplete="current-password"></label>
        <button class="btn btn-primary btn-block" type="submit">登录</button>
      </form>`;
    }

    function registerForm() {
      return `<form class="form-stack" data-register-form>
        <label>手机号<input class="input" name="phone" placeholder="请输入手机号" autocomplete="tel"></label>
        <label>验证码<div class="input-row"><input class="input" name="code" placeholder="123456"><button type="button" class="btn btn-ghost btn-sm" data-send-code>发送验证码</button></div></label>
        <label>密码<input class="input" type="password" name="password" placeholder="8–64 位，含字母和数字"></label>
        <label>姓名<input class="input" name="name" placeholder="例如 陈子昂"></label>
        <label>主位置<select class="input" name="position"><option value="MIDFIELDER">中场</option><option value="FORWARD">前锋</option><option value="DEFENDER">后卫</option><option value="GOALKEEPER">门将</option></select></label>
        <button class="btn btn-primary btn-block" type="submit">注册并进入</button>
        <p class="muted small">自助注册会进入待审核状态；邀请链接注册可免审（原型不展开）。</p>
      </form>`;
    }

    function bind() {
      $$('[data-mode]', host).forEach((button) => {
        button.addEventListener('click', () => {
          mode = button.dataset.mode;
          render();
        });
      });
      $$('[data-demo-id]', host).forEach((button) => {
        button.addEventListener('click', () => {
          Y.setUser(Number(button.dataset.demoId), redirect);
        });
      });
      const loginFormElement = $('[data-login-form]', host);
      if (loginFormElement) {
        loginFormElement.addEventListener('submit', (event) => {
          event.preventDefault();
          const data = new FormData(loginFormElement);
          const phone = String(data.get('phone') || '').trim();
          const matched = Object.entries(Y.DEMO_PHONES).find(([, value]) => value === phone);
          if (matched) {
            Y.setUser(Number(matched[0]), redirect);
            return;
          }
          const registered = Y.state.players.find((player) => player.phone === phone);
          if (registered) {
            Y.setUser(registered.id, redirect);
            return;
          }
          Y.toast('账号不存在，请用下方快速体验或注册');
        });
      }
      const registerFormElement = $('[data-register-form]', host);
      if (registerFormElement) {
        registerFormElement.addEventListener('submit', (event) => {
          event.preventDefault();
          const data = new FormData(registerFormElement);
          const phone = String(data.get('phone') || '').trim();
          const code = String(data.get('code') || '').trim();
          const password = String(data.get('password') || '');
          const name = String(data.get('name') || '').trim();
          if (!/^1[3-9]\d{9}$/.test(phone)) {
            Y.toast('请输入正确的手机号');
            return;
          }
          if (code !== '123456') {
            Y.toast('验证码错误（演示验证码 123456）');
            return;
          }
          if (password.length < 8 || password.length > 64 || !/[A-Za-z]/.test(password) || !/\d/.test(password)) {
            Y.toast('密码需为 8–64 位，且包含字母和数字');
            return;
          }
          const duplicate = Object.values(Y.DEMO_PHONES).includes(phone) ||
            Y.state.players.some((player) => player.phone === phone);
          if (duplicate) {
            Y.toast('手机号已注册');
            return;
          }
          const newId = Math.max(...Y.state.players.map((player) => player.id)) + 1;
          Y.state.players.push({
            id: newId,
            name: name || '新球员',
            phone,
            jersey: 30 + (newId % 10),
            position: String(data.get('position') || 'MIDFIELDER'),
            secondary: [],
            region: '上海',
            type: 'GUEST',
            status: 'PENDING',
            skill: 3,
            appearances: 0,
            goals: 0,
            assists: 0,
            color: '#0f766e'
          });
          Y.save();
          Y.addAudit(name || '新球员', '自助注册', phone);
          Y.setUser(newId, redirect);
        });
      }
      const sendCode = $('[data-send-code]', host);
      if (sendCode) {
        sendCode.addEventListener('click', () => {
          Y.toast('验证码已发送（演示验证码 123456）');
        });
      }
    }

    render();
  }

  function openProfileEditor(user) {
    const positions = Y.GROUP_POSITIONS.map((code) => [code, Y.positionLabel(code)]);
    const secondary = Array.isArray(user.secondary) ? user.secondary : [];
    let pendingAvatar = user.avatar || '';
    const body = `<form id="profile-form" class="form-grid">
      <div class="full table-actions">
        <span data-avatar-preview>${Y.avatar(user, 'xl')}</span>
        <label class="btn btn-ghost btn-sm">上传头像<input type="file" accept="image/*" data-avatar-file hidden></label>
        <button type="button" class="btn btn-ghost btn-sm" data-avatar-remove>移除头像</button>
        <span class="muted small">支持 JPG/PNG，最大 2MB，自动压缩</span>
      </div>
      <label>姓名<input class="input" name="name" value="${esc(user.name)}" required></label>
      <label>地区<input class="input" name="region" value="${esc(user.region || '')}"></label>
      <label>主位置<select class="input" name="position">${positions.map(([value, label]) => `<option value="${value}"${user.position === value ? ' selected' : ''}>${label}</option>`).join('')}</select></label>
      <div class="full"><span class="muted small">副位置</span><div class="chip-row">${positions.map(([value, label]) => `<label class="checkline"><input type="checkbox" name="secondary" value="${value}"${secondary.includes(value) ? ' checked' : ''}${user.position === value ? ' disabled' : ''}> ${label}</label>`).join('')}</div></div>
      <p class="muted small full">号码、星级和角色由管理员维护。</p>
    </form>`;
    const modal = Y.openModal('编辑资料', body, '<button class="btn btn-primary" id="profile-submit">保存</button><button class="btn btn-ghost" data-close-modal>取消</button>');
    const preview = modal.querySelector('[data-avatar-preview]');
    const paintPreview = () => { preview.innerHTML = Y.avatar({ ...user, avatar: pendingAvatar }, 'xl'); };
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
    modal.querySelector('#profile-submit').addEventListener('click', () => {
      const form = modal.querySelector('#profile-form');
      if (!form.reportValidity()) return;
      const data = new FormData(form);
      const result = Y.updateProfile(user.id, {
        name: String(data.get('name')),
        region: String(data.get('region') || ''),
        position: String(data.get('position')),
        secondary: Array.from(data.getAll('secondary')).filter((code) => code !== String(data.get('position'))),
        avatar: pendingAvatar
      });
      if (!result.ok) {
        Y.toast(result.message);
        return;
      }
      Y.closeModal();
      Y.toast('资料已更新');
      location.reload();
    });
  }

  function initProfile() {
    const host = $('#profile-content');
    if (!host) return;
    const user = Y.currentUser();
    if (!user.id) {
      host.innerHTML = `<div class="container section"><div class="empty-state card"><h2>还没有登录</h2><p>登录后查看个人资料、通知和报名记录。</p><a class="btn btn-primary" href="login.html">去登录</a></div></div>`;
      return;
    }
    const role = Y.userRole(user);
    const notifications = Y.state.notifications
      .filter((notification) => notification.userId === user.id)
      .sort((a, b) => new Date(b.at) - new Date(a.at));
    const allRegistrations = Y.state.matches
      .map((match) => {
        const records = (match.registrations || [])
          .filter((registration) => Number(registration.playerId) === Number(user.id))
          .sort((a, b) => new Date(b.at || 0) - new Date(a.at || 0));
        return records[0] ? { match, registration: records[0] } : null;
      })
      .filter(Boolean);
    const activeRegistrations = allRegistrations.filter(({ match, registration }) =>
      !Y.isTerminalMatch(match) && ['CONFIRMED', 'WAITLISTED'].includes(registration.status));
    const historyRegistrations = allRegistrations.filter((item) => !activeRegistrations.includes(item));
    const phone = user.phone || Y.DEMO_PHONES[user.id] || '';
    host.innerHTML = `<div class="container section">
      ${user.status === 'PENDING' ? '<div class="banner banner-warn">账号待审核：可以浏览公开内容，暂不能报名、签到或录事件。</div>' : ''}
      <div class="profile-grid">
        <section class="card profile-card">
          <div class="profile-top">${Y.avatar(user, 'xl')}<div><h1>${esc(user.name)}</h1><p class="muted">${Y.roleLabel(role)} · ${user.status === 'ACTIVE' ? '正常' : user.status === 'PENDING' ? '待审核' : '已注销'}</p></div></div>
          <div class="profile-fields">
            <div><span>手机号</span><b>${Y.maskPhone(phone)}</b></div>
            <div><span>球衣号</span><b>#${user.jersey ?? '—'}</b></div>
            <div><span>主位置</span><b>${Y.positionLabel(user.position)}</b></div>
            <div><span>地区</span><b>${esc(user.region || '—')}</b></div>
            <div><span>星级</span><b>${Y.stars(user.skill)}</b></div>
            <div><span>角色</span><b>${Y.rolesOf(user).map(Y.roleLabel).join(' / ')}</b></div>
          </div>
          <button class="btn btn-primary btn-block" data-edit-profile>编辑资料</button>
          <button class="btn btn-ghost btn-block" data-logout>退出登录</button>
        </section>
        <section class="card">
          <div class="card-head"><h2>通知</h2><button class="btn btn-ghost btn-sm" data-read-all>全部已读</button></div>
          ${notifications.length
            ? notifications.map((notification) => `<div class="notice ${notification.read ? 'read' : ''}"><div><strong>${esc(notification.title)}</strong><p>${esc(notification.body)}</p></div><small>${Y.formatRelative(notification.at)}</small>${notification.read ? '' : `<button class="btn btn-ghost btn-sm" data-read-notice="${notification.id}">已读</button>`}</div>`).join('')
            : '<div class="empty-inline">暂无通知</div>'}
        </section>
        <section class="card">
          <div class="card-head"><h2>我的报名</h2><span class="muted">${allRegistrations.length} 场</span></div>
          <div class="pool-title"><span>进行中</span><span>${activeRegistrations.length} 场</span></div>
          ${activeRegistrations.length
            ? activeRegistrations.map(({ match, registration }) => {
              const badge = Y.registrationBadgeMeta(match, registration);
              return `<a class="registration-row" href="match.html?id=${match.id}"><div><strong>${esc(match.title)}</strong><p class="muted small">${Y.formatDateTime(match.start)} · ${esc(match.venue)}</p></div><span class="badge ${badge.cls}">${badge.label}</span></a>`;
            }).join('')
            : '<div class="empty-inline">暂无进行中的报名</div>'}
          <div class="pool-title"><span>历史</span><span>${historyRegistrations.length} 场</span></div>
          ${historyRegistrations.length
            ? historyRegistrations.map(({ match, registration }) => {
              const badge = Y.registrationBadgeMeta(match, registration);
              return `<a class="registration-row" href="match.html?id=${match.id}"><div><strong>${esc(match.title)}</strong><p class="muted small">${Y.formatDateTime(match.start)} · ${esc(match.venue)}</p></div><span class="badge ${badge.cls}">${badge.label}</span></a>`;
            }).join('')
            : '<div class="empty-inline">暂无历史报名</div>'}
        </section>
      </div>
    </div>`;
    const editProfile = $('[data-edit-profile]', host);
    if (editProfile) {
      editProfile.addEventListener('click', () => openProfileEditor(user));
    }
    const logout = $('[data-logout]', host);
    if (logout) {
      logout.addEventListener('click', () => {
        Y.setUser(0);
      });
    }
    $$('[data-read-notice]', host).forEach((button) => {
      button.addEventListener('click', () => {
        const notice = Y.state.notifications.find((item) => item.id === button.dataset.readNotice);
        if (!notice) return;
        notice.read = true;
        Y.save();
        location.reload();
      });
    });
    const readAll = $('[data-read-all]', host);
    if (readAll) {
      readAll.addEventListener('click', () => {
        Y.state.notifications.forEach((notification) => {
          if (notification.userId === user.id) notification.read = true;
        });
        Y.save();
        Y.toast('已全部标记为已读');
        initProfile();
      });
    }
  }
})();
