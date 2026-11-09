(function () {
  const Y = window.YHFC;
  const { DATA, state, save, addAudit, currentUser, escapeHtml, positionShort, STORAGE_KEY } = Y;
  if (!STORAGE_KEY) throw new Error('lineup.js 需要在 core.js 之后加载');

  function allFormations() {
    return DATA.formations.concat(state.customFormations || []);
  }

  function formationById(id) {
    return allFormations().find((formation) => formation.id === id) || DATA.formations[0];
  }

  function pitchHtml(lineup, playerMap, editable) {
    const data = lineup || {};
    const formation = formationById(data.formationId);
    const starters = data.starters || {};
    return `<div class="pitch">
      <div class="pitch-line pitch-line-half"></div>
      <div class="pitch-circle"></div>
      <div class="pitch-box pitch-box-top"></div>
      <div class="pitch-box pitch-box-bottom"></div>
      ${formation.slots.map((slot) => {
        const player = starters[slot.id] ? playerMap[starters[slot.id]] : null;
        return `<div class="pitch-slot ${player ? 'filled' : ''}" data-slot="${slot.id}"
          style="left:${slot.x}%;top:${slot.y}%"
          ${editable && player ? `draggable="true" data-drag-starter="${slot.id}"` : ''}>
          ${player
            ? `<span class="pitch-num">${player.jersey}</span><span class="pitch-name">${escapeHtml(player.name)}</span>`
            : `<span class="pitch-empty">${positionShort(slot.role)}</span>`}
        </div>`;
      }).join('')}
    </div>`;
  }

  function currentFormation(lineup) {
    return formationById(lineup && lineup.formationId);
  }

  function persistedLineupVersion(matchId, side) {
    try {
      const persisted = JSON.parse(localStorage.getItem(STORAGE_KEY) || 'null');
      const match = ((persisted && persisted.matches) || []).find((item) => item.id === matchId);
      return Number(match && match.lineups && match.lineups[side] && match.lineups[side].version) || 0;
    } catch (error) {
      return 0;
    }
  }

  function lineupEligiblePlayers(match, side) {
    if (!match || isOpponentLineup(match, side)) return [];
    const confirmed = Y.confirmed(match);
    const confirmedIds = new Set(confirmed.map((registration) => Number(registration.playerId)));
    if (Y.INTERNAL_TYPES.includes(match.type) && match.grouping) {
      const group = groupingTeamForSide(side);
      return (match.grouping[group] || [])
        .map((id) => Y.playerById(id))
        .filter((player) => player && player.status === 'ACTIVE' && confirmedIds.has(Number(player.id)));
    }
    return confirmed
      .map((registration) => Y.playerById(registration.playerId))
      .filter((player) => player && player.status === 'ACTIVE');
  }

  function commitLineup(match, side, expectedVersion, actorName) {
    if (!Y.hasPermission('lineup:edit:any')) return { ok: false, message: '没有编辑阵容的权限' };
    if (Y.isTerminalMatch(match) || match.status === 'POSTPONED') return { ok: false, message: '已取消、已结束或已延期的比赛不能编辑阵容' };
    const remoteVersion = persistedLineupVersion(match.id, side);
    if (remoteVersion !== Number(expectedVersion)) {
      return { ok: false, conflict: true, message: '阵容已被其他人修改，请刷新页面后重试' };
    }
    const lineup = match.lineups[side];
    if (!isOpponentLineup(match, side)) {
      const eligible = lineupEligiblePlayers(match, side);
      const eligibleIds = new Set(eligible.map((player) => Number(player.id)));
      const allIds = [
        ...Object.values(lineup.starters || {}).map(Number).filter(Boolean),
        ...(lineup.bench || []).map(Number).filter(Boolean)
      ];
      if (allIds.some((id) => !eligibleIds.has(id))) {
        const sideName = side === 'HOME' ? Y.teamNames(match).home : Y.teamNames(match).away;
        const grouped = Y.INTERNAL_TYPES.includes(match.type) && Boolean(match.grouping);
        return { ok: false, message: grouped ? `${sideName}阵容只能使用${sideName}分组成员` : '阵容只能使用本场确认报名球员' };
      }
      if (new Set(allIds).size !== allIds.length) {
        return { ok: false, message: '同一球员不能同时出现在首发和替补' };
      }
      const starters = Object.values(lineup.starters || {}).filter(Boolean).length;
      if (starters !== 8) return { ok: false, message: '首发必须满 8 人才能保存' };
    }
    lineup.version = Math.max(remoteVersion, Number(lineup.version) || 0) + 1;
    lineup.updatedAt = new Date().toISOString();
    lineup.updatedBy = actorName || currentUser().name;
    save();
    addAudit(actorName || currentUser().name, '保存阵容', `${match.title} / ${side === 'HOME' ? '主队' : '客队'}`, { after: { version: lineup.version } });
    return { ok: true, version: lineup.version };
  }

  function addToBench(lineup, playerId) {
    if (!lineup) return;
    if (!Array.isArray(lineup.bench)) lineup.bench = [];
    if (!lineup.bench.includes(Number(playerId))) lineup.bench.push(Number(playerId));
  }

  function removeFromBench(lineup, playerId) {
    if (!lineup || !Array.isArray(lineup.bench)) return;
    lineup.bench = lineup.bench.filter((id) => Number(id) !== Number(playerId));
  }

  function isOpponentLineup(match, side) {
    if (!['OFFICIAL', 'FRIENDLY'].includes(match.type)) return false;
    const own = match.ourSide === 'AWAY' ? 'AWAY' : 'HOME';
    return side !== own;
  }

  function ourLineupSide(match) {
    return match.ourSide === 'AWAY' ? 'AWAY' : 'HOME';
  }

  function groupingTeamForSide(side) {
    return side === 'AWAY' ? 'B' : 'A';
  }

  function groupingTeamName(match, team) {
    return team === 'B' ? Y.teamNames(match).away : Y.teamNames(match).home;
  }

  Object.assign(Y, { allFormations, formationById, pitchHtml, currentFormation, persistedLineupVersion, lineupEligiblePlayers, commitLineup, addToBench, removeFromBench , isOpponentLineup, ourLineupSide, groupingTeamForSide, groupingTeamName });
})();
