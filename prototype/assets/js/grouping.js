(function () {
  const Y = window.YHFC;
  const { save, addAudit, currentUser, playerById, confirmed, clone, positionLabel,
    GROUP_POSITIONS, OUTFIELD_POSITIONS, rosterFingerprint, INTERNAL_TYPES } = Y;
  if (typeof confirmed !== 'function') throw new Error('grouping.js 需要在 match.js 之后加载');

  function groupingEligiblePlayers(match) {
    const seen = new Set();
    return confirmed(match)
      .filter((registration) => match.includeGuests || registration.type !== 'GUEST')
      .map((registration) => playerById(registration.playerId))
      .filter((player) => {
        if (!player || player.status !== 'ACTIVE' || seen.has(player.id)) return false;
        if (!match.includeGuests && player.type === 'GUEST') return false;
        seen.add(player.id);
        return true;
      });
  }

  function groupingProfiles(players) {
    // 分组直接使用球员星级，缺失时按默认 3.00。
    return players.map((player) => {
      const raw = Number(player.skill);
      const final = Number.isFinite(raw) ? Math.max(1, Math.min(5, raw)) : 3;
      return { player, final };
    });
  }

  function positionCountsOf(ids) {
    const counts = {};
    Object.keys(Y.POSITION_META).forEach((code) => { counts[code] = 0; });
    (ids || []).forEach((id) => {
      const player = playerById(id);
      if (player && counts[player.position] != null) counts[player.position] += 1;
    });
    return counts;
  }

  function groupingObjective(A, B, profiles, gkPool) {
    const sizeDiff = Math.abs(A.length - B.length);
    const totals = { A: 0, B: 0 };
    A.forEach((id) => { totals.A += profiles[id] ? profiles[id].final : 0; });
    B.forEach((id) => { totals.B += profiles[id] ? profiles[id].final : 0; });
    const scoreDiff = Math.abs(totals.A - totals.B);
    const posA = positionCountsOf(A);
    const posB = positionCountsOf(B);
    const positionImbalance = OUTFIELD_POSITIONS
      .reduce((sum, key) => sum + Math.abs(posA[key] - posB[key]), 0);
    const gkMissing = gkPool >= 2 && (posA.GOALKEEPER === 0 || posB.GOALKEEPER === 0);
    return { tuple: [gkMissing ? 1 : 0, sizeDiff > 1 ? 1 : 0, scoreDiff, positionImbalance], totals, scoreDiff, positionImbalance };
  }

  function buildGrouping(match) {
    if (!Y.hasPermission('grouping:generate:any')) return { ok: false, message: '没有生成分组的权限' };
    if (!INTERNAL_TYPES.includes(match.type)) {
      return { ok: false, message: '仅队内赛/训练赛支持分组' };
    }
    if (match.status !== 'LOCKED') {
      return { ok: false, message: '只有锁定状态才能生成分组' };
    }
    const eligible = groupingEligiblePlayers(match);
    if (eligible.length < 2) return { ok: false, message: '可分组球员不足' };
    const profileList = groupingProfiles(eligible);
    const profiles = {};
    profileList.forEach((profile) => { profiles[profile.player.id] = profile; });
    const gkPool = eligible.filter((player) => player.position === 'GOALKEEPER').length;
    const A = [];
    const B = [];
    const randomRank = {};
    eligible.forEach((player) => {
      randomRank[player.id] = (Number(profiles[player.id].final) || 0) + (Math.random() - 0.5) * 1.2;
    });
    GROUP_POSITIONS.forEach((position) => {
      const group = eligible
        .filter((player) => player.position === position)
        .sort((a, b) => (randomRank[b.id] - randomRank[a.id]) || (a.jersey - b.jersey) || (a.id - b.id));
      let team = Math.random() < 0.5 ? 'A' : 'B';
      group.forEach((player) => {
        (team === 'A' ? A : B).push(player.id);
        team = team === 'A' ? 'B' : 'A';
      });
    });
    while (Math.abs(A.length - B.length) > 1) {
      const from = A.length > B.length ? A : B;
      const to = A.length > B.length ? B : A;
      from.sort((a, b) => profiles[a].final - profiles[b].final);
      to.push(from.shift());
    }
    if (gkPool >= 2) {
      const aGk = A.filter((id) => playerById(id)?.position === 'GOALKEEPER').length;
      const bGk = B.filter((id) => playerById(id)?.position === 'GOALKEEPER').length;
      if (!aGk || !bGk) {
        const giver = aGk >= bGk ? A : B;
        const taker = aGk >= bGk ? B : A;
        const gk = giver.find((id) => playerById(id)?.position === 'GOALKEEPER');
        const other = taker.find((id) => playerById(id)?.position !== 'GOALKEEPER');
        if (gk && other) {
          giver[giver.indexOf(gk)] = other;
          taker[taker.indexOf(other)] = gk;
        }
      }
    }
    const cap = Math.max(20, eligible.length * eligible.length);
    let current = groupingObjective(A, B, profiles, gkPool);
    for (let round = 0; round < cap; round += 1) {
      let best = null;
      for (let i = 0; i < A.length; i += 1) {
        for (let j = 0; j < B.length; j += 1) {
          const nextA = A.slice();
          const nextB = B.slice();
          [nextA[i], nextB[j]] = [nextB[j], nextA[i]];
          const objective = groupingObjective(nextA, nextB, profiles, gkPool);
          const better = objective.tuple.some((value, index) => {
            for (let k = 0; k < index; k += 1) {
              if (objective.tuple[k] !== current.tuple[k]) return false;
            }
            return value < current.tuple[index];
          });
          if (better && (!best || objective.tuple.some((value, index) => {
            for (let k = 0; k < index; k += 1) {
              if (objective.tuple[k] !== best.objective.tuple[k]) return false;
            }
            return value < best.objective.tuple[index];
          }))) {
            best = { i, j, objective };
          }
        }
      }
      if (!best) break;
      [A[best.i], B[best.j]] = [B[best.j], A[best.i]];
      current = best.objective;
    }
    const warnings = [];
    const posA = positionCountsOf(A);
    const posB = positionCountsOf(B);
    if (gkPool === 0) warnings.push('名单中没有门将');
    if (gkPool === 1) warnings.push('名单中仅 1 名门将，无法保证每队至少 1 名门将');
    if (gkPool >= 2 && (posA.GOALKEEPER === 0 || posB.GOALKEEPER === 0)) warnings.push('有球队缺少门将');
    OUTFIELD_POSITIONS.forEach((position) => {
      const count = eligible.filter((player) => player.position === position).length;
      if (count < 2) warnings.push(`${positionLabel(position)}位置仅 ${count} 人，位置均衡可能受限`);
    });
    const scores = {};
    Object.keys(profiles).forEach((id) => {
      const profile = profiles[id];
      scores[id] = { final: profile.final };
    });
    return {
      ok: true,
      grouping: {
        A,
        B,
        scores,
        rosterFingerprint: rosterFingerprint(eligible.map((player) => player.id)),
        generatedAt: new Date().toISOString(),
        savedAt: null,
        warnings,
        snapshot: null
      }
    };
  }

  function groupingSummary(match, grouping) {
    const A = (grouping && grouping.A) || [];
    const B = (grouping && grouping.B) || [];
    const scores = (grouping && grouping.scores) || {};
    const valueOf = (id) => scores[id] ? Number(scores[id].final) : (playerById(id)?.skill || 0);
    const totalA = A.reduce((sum, id) => sum + valueOf(id), 0);
    const totalB = B.reduce((sum, id) => sum + valueOf(id), 0);
    const posA = positionCountsOf(A);
    const posB = positionCountsOf(B);
    const eligible = groupingEligiblePlayers(match);
    const gkPool = eligible.filter((player) => player.position === 'GOALKEEPER').length;
    const warnings = [];
    if (Math.abs(A.length - B.length) > 1) warnings.push('两队人数差超过 1');
    if (gkPool >= 2 && (posA.GOALKEEPER === 0 || posB.GOALKEEPER === 0)) warnings.push('有球队缺少门将');
    Y.GROUP_POSITIONS.forEach((code) => {
      const diff = Math.abs((posA[code] || 0) - (posB[code] || 0));
      if (diff > 1) warnings.push(`${Y.positionLabel(code)}人数差 ${diff}`);
    });
    return {
      totalA,
      totalB,
      diff: Math.abs(totalA - totalB),
      posA,
      posB,
      warnings,
      rosterDiff: grouping ? groupingRosterDiff(match, grouping) : { changed: false, addedIds: [], removedIds: [] },
      snapshot: grouping && grouping.snapshot
    };
  }

  function groupingRosterDiff(match, grouping) {
    const current = groupingEligiblePlayers(match).map((player) => player.id);
    const saved = String((grouping && grouping.rosterFingerprint) || '')
      .split(',').filter(Boolean).map(Number);
    const currentSet = new Set(current);
    const savedSet = new Set(saved);
    return {
      changed: rosterFingerprint(current) !== rosterFingerprint(saved),
      addedIds: current.filter((id) => !savedSet.has(id)),
      removedIds: saved.filter((id) => !currentSet.has(id))
    };
  }

  function saveGroupingSnapshot(match, actorName) {
    if (!Y.hasPermission('grouping:generate:any')) return { ok: false, message: '没有保存分组的权限' };
    const grouping = match.grouping;
    if (!grouping) return { ok: false, message: '尚未生成分组' };
    if (match.status !== 'LOCKED') return { ok: false, message: '只有锁定状态才能保存分组' };
    const summary = groupingSummary(match, grouping);
    grouping.savedAt = new Date().toISOString();
    grouping.snapshot = {
      version: 1,
      savedAt: grouping.savedAt,
      generatedAt: grouping.generatedAt || null,
      rosterFingerprint: grouping.rosterFingerprint,
      players: clone(grouping.scores || {}),
      teamTotals: { A: summary.totalA, B: summary.totalB },
      teamDiff: summary.diff,
      positionDistribution: { A: summary.posA, B: summary.posB }
    };
    save();
    addAudit(actorName || currentUser().name, '保存分组', match.title, { after: grouping.snapshot });
    return { ok: true };
  }

  function combinedWarnings(grouping, summary) {
    return Array.from(new Set([
      ...((grouping && grouping.warnings) || []),
      ...((summary && summary.warnings) || [])
    ]));
  }

  Object.assign(Y, { groupingEligiblePlayers, groupingProfiles, positionCountsOf, groupingObjective, buildGrouping, groupingSummary, groupingRosterDiff, saveGroupingSnapshot, combinedWarnings });
})();
