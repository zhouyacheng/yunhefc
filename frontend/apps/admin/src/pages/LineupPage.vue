<script setup lang="ts">
import { ERROR_CODES, isApiError, newIdempotencyKey } from '@yunhefc/api-client';
import {
  GROUPING_VIEW_ANY,
  LINEUP_EDIT_ANY,
  LINEUP_VIEW_ANY,
  hasPermission,
} from '@yunhefc/permissions';
import type {
  FormationTemplate,
  FormationTemplateCreateRequest,
  FormationTemplateListResponse,
  FormationTemplateRenameRequest,
  FormationTemplateResponse,
  GroupingAdmin,
  LineupAdmin,
  LineupSaveRequest,
  LineupSaveResponse,
  LineupSide,
  LineupTemplateResponse,
  MatchSummary,
  Page,
  RegistrationRow,
} from '@yunhefc/shared';
import {
  MATCH_STATUS_TEXT,
  formatDateTime,
  isInternalMatchType,
  isOpponentLineupSide,
  lineupEditable,
  positionText,
} from '@yunhefc/shared';
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute } from 'vue-router';

import { api, publicApi } from '../api/client';
import { useSessionStore } from '../stores/session';

interface PlayerLike {
  id: number;
  name: string;
  jersey?: number | null;
  position: number;
  positionLabel?: string | null;
}

const route = useRoute();
const session = useSessionStore();

const matches = ref<MatchSummary[]>([]);
const selectedMatchId = ref<number | null>(null);
const side = ref<LineupSide>(0);
const lineup = ref<LineupAdmin | null>(null);
const roster = ref<RegistrationRow[]>([]);
const grouping = ref<GroupingAdmin | null>(null);
const templates = ref<FormationTemplate[]>([]);

const loadingMatches = ref(false);
const loadingLineup = ref(false);
const loadingRoster = ref(false);
const loadingGrouping = ref(false);
const loadingTemplates = ref(false);
const saving = ref(false);

const pageError = ref('');
const lineupError = ref('');
const rosterError = ref('');
const groupingError = ref('');
const actionError = ref('');
const conflictMessage = ref('');

const draftFormationId = ref(0);
const draftStarters = ref<Record<string, number | null>>({});
const draftBench = ref<number[]>([]);
const selectedPlayer = ref<{ playerId: number } | null>(null);

const canView = computed(() => hasPermission(session.permissions, LINEUP_VIEW_ANY));
const canEdit = computed(() => hasPermission(session.permissions, LINEUP_EDIT_ANY));
const canViewGrouping = computed(() => hasPermission(session.permissions, GROUPING_VIEW_ANY));

const selectedMatch = computed(
  () => matches.value.find((match) => match.id === selectedMatchId.value) ?? null,
);
const internalMatch = computed(() => isInternalMatchType(selectedMatch.value?.type));
const opponent = computed(() => isOpponentLineupSide(selectedMatch.value, side.value));
const savedGrouping = computed(() =>
  internalMatch.value && grouping.value?.savedAt ? grouping.value : null,
);

const presets = computed(() => templates.value.filter((template) => template.isPreset));
const customs = computed(() => templates.value.filter((template) => !template.isPreset));

const formationOptions = computed(() => {
  const options = [...templates.value];
  if (lineup.value && !options.some((item) => item.id === lineup.value?.formation.id)) {
    options.unshift(lineup.value.formation);
  }
  return options;
});
const currentFormation = computed(
  () =>
    formationOptions.value.find((item) => item.id === draftFormationId.value) ??
    lineup.value?.formation ??
    null,
);

const confirmedRoster = computed(() => {
  const seen = new Set<number>();
  return roster.value
    .filter((row) => row.status === 0)
    .filter((row) => {
      if (seen.has(row.player.id)) {
        return false;
      }
      seen.add(row.player.id);
      return true;
    });
});
const confirmedPlayers = computed<PlayerLike[]>(() =>
  confirmedRoster.value.map((row) => ({
    id: row.player.id,
    name: row.player.name,
    jersey: row.player.jersey,
    position: row.player.position,
    positionLabel: row.player.positionLabel,
  })),
);

const eligiblePlayers = computed<PlayerLike[]>(() => {
  if (opponent.value) {
    return [];
  }
  if (internalMatch.value && savedGrouping.value) {
    const team = side.value === 0 ? 'A' : 'B';
    const confirmedIds = new Set(confirmedPlayers.value.map((player) => player.id));
    return savedGrouping.value.teams[team]
      .filter((player) => confirmedIds.has(player.playerId))
      .map((player) => ({
        id: player.playerId,
        name: player.name,
        jersey: player.jersey,
        position: player.position,
      }));
  }
  return confirmedPlayers.value;
});

const playerMap = computed(() => {
  const map = new Map<number, PlayerLike>();
  const add = (player: PlayerLike): void => {
    if (!map.has(player.id)) {
      map.set(player.id, player);
    }
  };
  eligiblePlayers.value.forEach(add);
  lineup.value?.starters.forEach((starter) => {
    if (starter.player) {
      add(starter.player);
    }
  });
  lineup.value?.bench.forEach(add);
  confirmedPlayers.value.forEach(add);
  if (grouping.value) {
    for (const team of ['A', 'B'] as const) {
      grouping.value.teams[team].forEach((player) =>
        add({
          id: player.playerId,
          name: player.name,
          jersey: player.jersey,
          position: player.position,
        }),
      );
    }
  }
  return map;
});

function playerFor(playerId: number): PlayerLike {
  return (
    playerMap.value.get(playerId) ?? {
      id: playerId,
      name: `球员#${playerId}`,
      jersey: null,
      position: -1,
    }
  );
}

function playerLabel(player: PlayerLike): string {
  const jersey = player.jersey === null || player.jersey === undefined ? '' : ` #${player.jersey}`;
  return `${player.name}${jersey} · ${positionText(player.position)}`;
}

const starterPlayerIds = computed(
  () =>
    new Set(
      Object.values(draftStarters.value).filter(
        (playerId): playerId is number => typeof playerId === 'number',
      ),
    ),
);
const benchPlayerIds = computed(() => new Set(draftBench.value));
const starterCount = computed(() => starterPlayerIds.value.size);
const benchPlayers = computed(() => draftBench.value.map(playerFor));
const unassignedPlayers = computed(() =>
  eligiblePlayers.value.filter(
    (player) => !starterPlayerIds.value.has(player.id) && !benchPlayerIds.value.has(player.id),
  ),
);

function starterOf(slotKey: string): PlayerLike | null {
  const playerId = draftStarters.value[slotKey];
  return typeof playerId === 'number' ? playerFor(playerId) : null;
}

const dirty = computed(() => {
  const current = lineup.value;
  if (!current || !currentFormation.value) {
    return false;
  }
  if (currentFormation.value.id !== current.formation.id) {
    return true;
  }
  const saved = new Map(
    current.starters.map((starter) => [starter.slotKey, starter.player?.id ?? null]),
  );
  const slotsDirty = currentFormation.value.slots.some(
    (slot) => (draftStarters.value[slot.slotKey] ?? null) !== (saved.get(slot.slotKey) ?? null),
  );
  if (slotsDirty) {
    return true;
  }
  const savedBench = current.bench.map((player) => player.id).sort((a, b) => a - b);
  const draft = [...draftBench.value].sort((a, b) => a - b);
  return savedBench.length !== draft.length || savedBench.some((id, index) => id !== draft[index]);
});

const saveDisabledReason = computed(() => {
  if (!selectedMatch.value) {
    return '';
  }
  if (!canEdit.value) {
    return '当前账号没有编辑阵容权限，只能只读查看。';
  }
  if (!lineupEditable(selectedMatch.value.status)) {
    return '已结束、已取消或已延期的比赛不能保存阵容。';
  }
  if (!opponent.value && starterCount.value !== 8) {
    return '首发需排满 8 人后才能保存。';
  }
  return '';
});

const groupingChanged = computed(() => Boolean(grouping.value?.rosterDiff.changed));
const rosterDiffNames = computed(() => {
  const names = new Map(confirmedPlayers.value.map((player) => [player.id, player.name]));
  const diff = grouping.value?.rosterDiff;
  if (!diff) {
    return { added: [] as string[], removed: [] as string[] };
  }
  return {
    added: diff.addedPlayerIds.map((id) => names.get(id) ?? `球员#${id}`),
    removed: diff.removedPlayerIds.map((id) => playerMap.value.get(id)?.name ?? `球员#${id}`),
  };
});
const ungroupedPlayers = computed(() => {
  const byId = new Map(confirmedPlayers.value.map((player) => [player.id, player]));
  return (grouping.value?.ungroupedPlayerIds ?? []).map((id) => byId.get(id) ?? playerFor(id));
});
const nonParticipating = computed(() => {
  if (!internalMatch.value || !grouping.value) {
    return [];
  }
  const grouped = new Set<number>();
  for (const team of ['A', 'B'] as const) {
    grouping.value.teams[team].forEach((player) => grouped.add(player.playerId));
  }
  const ungrouped = new Set(grouping.value.ungroupedPlayerIds);
  return confirmedRoster.value
    .filter((row) => row.type === 0 && !grouped.has(row.player.id) && !ungrouped.has(row.player.id))
    .map((row) => row.player);
});

function groupingTeamLabel(team: 'A' | 'B'): string {
  if (!selectedMatch.value) {
    return team === 'A' ? 'A 队' : 'B 队';
  }
  return team === 'A' ? selectedMatch.value.homeTeamName : selectedMatch.value.awayTeamName;
}

function slotSummary(template: FormationTemplate): string {
  return [0, 1, 2, 3]
    .map((role) => ({ role, count: template.slots.filter((slot) => slot.role === role).length }))
    .filter((item) => item.count > 0)
    .map((item) => `${positionText(item.role)} ${item.count}`)
    .join(' · ');
}

// ---------------------------------------------------------------- loading

async function loadMatches(): Promise<void> {
  loadingMatches.value = true;
  pageError.value = '';
  try {
    const result = await publicApi.get<Page<MatchSummary>>('/matches', {
      query: { scope: 'all', sort: 'startAt:desc', page: 1, pageSize: 100 },
    });
    matches.value = result.items;
    const requested = Number(route.query.match);
    if (Number.isInteger(requested) && !matches.value.some((match) => match.id === requested)) {
      try {
        const detail = await publicApi.get<MatchSummary>(`/matches/${requested}`);
        matches.value = [detail, ...matches.value];
      } catch {
        // 深链目标不存在时回退到列表首场。
      }
    }
    if (!matches.value.some((match) => match.id === selectedMatchId.value)) {
      selectedMatchId.value = matches.value.some((match) => match.id === requested)
        ? requested
        : (matches.value[0]?.id ?? null);
    }
    await refreshSelected();
  } catch (caught) {
    pageError.value = isApiError(caught) ? caught.message : '比赛列表加载失败，请稍后重试';
  } finally {
    loadingMatches.value = false;
  }
}

function defaultSide(match: MatchSummary): LineupSide {
  if (isInternalMatchType(match.type)) {
    return 0;
  }
  return (match.ourSide ?? 0) as LineupSide;
}

async function refreshSelected(): Promise<void> {
  const matchId = selectedMatchId.value;
  lineup.value = null;
  roster.value = [];
  grouping.value = null;
  lineupError.value = '';
  rosterError.value = '';
  groupingError.value = '';
  actionError.value = '';
  conflictMessage.value = '';
  selectedPlayer.value = null;
  if (matchId === null) {
    return;
  }
  const match = matches.value.find((item) => item.id === matchId);
  if (match) {
    side.value = defaultSide(match);
  }
  await Promise.all([
    loadLineup(matchId, side.value),
    loadRoster(matchId),
    isInternalMatchType(match?.type) && canViewGrouping.value
      ? loadGrouping(matchId)
      : Promise.resolve(),
  ]);
  // 分组/名单晚于阵容返回时，按最终可排名单再归一化一次草稿。
  if (matchId === selectedMatchId.value && lineup.value) {
    resetDraft(lineup.value);
  }
}

function onMatchChange(): void {
  void refreshSelected();
}

async function loadLineup(matchId: number, lineupSide: LineupSide): Promise<void> {
  loadingLineup.value = true;
  lineupError.value = '';
  conflictMessage.value = '';
  try {
    const result = await api.get<LineupAdmin>(`/matches/${matchId}/lineups/${lineupSide}`);
    if (matchId === selectedMatchId.value && lineupSide === side.value) {
      lineup.value = result;
      resetDraft(result);
    }
  } catch (caught) {
    if (matchId === selectedMatchId.value && lineupSide === side.value) {
      lineup.value = null;
      lineupError.value = isApiError(caught) ? caught.message : '阵容加载失败，请稍后重试';
    }
  } finally {
    if (matchId === selectedMatchId.value && lineupSide === side.value) {
      loadingLineup.value = false;
    }
  }
}

async function loadRoster(matchId: number): Promise<void> {
  loadingRoster.value = true;
  rosterError.value = '';
  try {
    const result = await api.get<Page<RegistrationRow>>(`/matches/${matchId}/registrations`, {
      query: { status: 0, page: 1, pageSize: 100 },
    });
    if (matchId === selectedMatchId.value) {
      roster.value = result.items;
    }
  } catch (caught) {
    if (matchId === selectedMatchId.value) {
      roster.value = [];
      rosterError.value = isApiError(caught) ? caught.message : '本场名单加载失败';
    }
  } finally {
    if (matchId === selectedMatchId.value) {
      loadingRoster.value = false;
    }
  }
}

async function loadGrouping(matchId: number): Promise<void> {
  loadingGrouping.value = true;
  groupingError.value = '';
  try {
    const result = await api.get<GroupingAdmin>(`/matches/${matchId}/grouping`);
    if (matchId === selectedMatchId.value) {
      grouping.value = result;
    }
  } catch (caught) {
    if (matchId === selectedMatchId.value) {
      grouping.value = null;
      groupingError.value = isApiError(caught) ? caught.message : '分组加载失败';
    }
  } finally {
    if (matchId === selectedMatchId.value) {
      loadingGrouping.value = false;
    }
  }
}

async function loadTemplates(): Promise<void> {
  if (!canEdit.value) {
    return;
  }
  loadingTemplates.value = true;
  try {
    const result = await api.get<FormationTemplateListResponse>('/formation-templates', {
      query: { includePreset: true },
    });
    templates.value = result.items;
  } catch (caught) {
    templates.value = [];
    actionError.value = isApiError(caught) ? caught.message : '阵型模板加载失败';
  } finally {
    loadingTemplates.value = false;
  }
}

function resetDraft(source: LineupAdmin): void {
  draftFormationId.value = source.formation.id;
  const eligibleIds = new Set(eligiblePlayers.value.map((player) => player.id));
  draftStarters.value = Object.fromEntries(
    source.starters.map((starter) => {
      const playerId = starter.player?.id ?? null;
      const allowed =
        playerId === null || opponent.value || !canEdit.value || eligibleIds.has(playerId);
      return [starter.slotKey, allowed ? playerId : null];
    }),
  );
  draftBench.value = source.bench
    .map((player) => player.id)
    .filter((playerId) => opponent.value || !canEdit.value || eligibleIds.has(playerId));
  selectedPlayer.value = null;
}

// ---------------------------------------------------------------- editing

async function onSideChange(): Promise<void> {
  selectedPlayer.value = null;
  if (selectedMatchId.value !== null) {
    await loadLineup(selectedMatchId.value, side.value);
  }
}

async function onFormationChange(nextId: number): Promise<void> {
  if (starterCount.value > 0) {
    try {
      await ElMessageBox.confirm('切换阵型会清空当前首发，确认继续？', '切换阵型', {
        type: 'warning',
        confirmButtonText: '切换',
        cancelButtonText: '取消',
      });
    } catch {
      draftFormationId.value = lineup.value?.formation.id ?? 0;
      return;
    }
  }
  draftFormationId.value = nextId;
  draftStarters.value = {};
  draftBench.value = eligiblePlayers.value.map((player) => player.id);
  selectedPlayer.value = null;
  actionError.value = '';
}

function selectBenchPlayer(playerId: number): void {
  if (!canEdit.value || opponent.value) {
    return;
  }
  selectedPlayer.value = selectedPlayer.value?.playerId === playerId ? null : { playerId };
}

function removeFromSlot(slotKey: string): number | null {
  const playerId = draftStarters.value[slotKey] ?? null;
  if (playerId !== null) {
    draftStarters.value = { ...draftStarters.value, [slotKey]: null };
  }
  return playerId;
}

function addToBench(playerId: number): void {
  if (!draftBench.value.includes(playerId)) {
    draftBench.value = [...draftBench.value, playerId];
  }
}

function placePlayer(playerId: number, slotKey: string): void {
  if (opponent.value || !canEdit.value) {
    return;
  }
  const existingSlot = Object.entries(draftStarters.value).find(
    ([key, value]) => value === playerId && key !== slotKey,
  )?.[0];
  if (existingSlot) {
    draftStarters.value = { ...draftStarters.value, [existingSlot]: null };
  }
  const displaced = draftStarters.value[slotKey];
  if (typeof displaced === 'number' && displaced !== playerId) {
    addToBench(displaced);
  }
  draftBench.value = draftBench.value.filter((id) => id !== playerId);
  draftStarters.value = { ...draftStarters.value, [slotKey]: playerId };
}

function onSlotClick(slotKey: string): void {
  if (!canEdit.value || opponent.value) {
    return;
  }
  const selected = selectedPlayer.value;
  if (selected) {
    placePlayer(selected.playerId, slotKey);
    selectedPlayer.value = null;
    return;
  }
  const playerId = removeFromSlot(slotKey);
  if (playerId !== null) {
    addToBench(playerId);
  }
}

function onBenchClick(playerId: number): void {
  if (opponent.value) {
    return;
  }
  selectBenchPlayer(playerId);
}

function removeFromBench(playerId: number): void {
  if (!canEdit.value) {
    return;
  }
  const slotKey = Object.entries(draftStarters.value).find(([, value]) => value === playerId)?.[0];
  if (slotKey) {
    draftStarters.value = { ...draftStarters.value, [slotKey]: null };
  }
  draftBench.value = draftBench.value.filter((id) => id !== playerId);
  if (selectedPlayer.value?.playerId === playerId) {
    selectedPlayer.value = null;
  }
}

function onDragStart(event: DragEvent, playerId: number): void {
  if (!canEdit.value || opponent.value) {
    event.preventDefault();
    return;
  }
  selectedPlayer.value = { playerId };
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move';
    event.dataTransfer.setData('text/plain', String(playerId));
  }
}

function onDragOver(event: DragEvent): void {
  if (canEdit.value && !opponent.value) {
    event.preventDefault();
  }
}

function onDropSlot(event: DragEvent, slotKey: string): void {
  event.preventDefault();
  if (!canEdit.value || opponent.value) {
    return;
  }
  const raw = event.dataTransfer?.getData('text/plain');
  const playerId = raw ? Number(raw) : selectedPlayer.value?.playerId;
  if (playerId !== undefined && Number.isInteger(playerId) && playerId > 0) {
    placePlayer(playerId, slotKey);
    selectedPlayer.value = null;
  }
}

function onDropBench(event: DragEvent): void {
  event.preventDefault();
  if (!canEdit.value || opponent.value) {
    return;
  }
  const raw = event.dataTransfer?.getData('text/plain');
  const playerId = raw ? Number(raw) : selectedPlayer.value?.playerId;
  if (playerId !== undefined && Number.isInteger(playerId) && playerId > 0) {
    const slotKey = Object.entries(draftStarters.value).find(
      ([, value]) => value === playerId,
    )?.[0];
    if (slotKey) {
      draftStarters.value = { ...draftStarters.value, [slotKey]: null };
    }
    addToBench(playerId);
    selectedPlayer.value = null;
  }
}

function switchGroupingTeam(team: 'A' | 'B'): void {
  const next = (team === 'A' ? 0 : 1) as LineupSide;
  if (next === side.value) {
    return;
  }
  side.value = next;
  void onSideChange();
}

// ---------------------------------------------------------------- saving

async function saveLineup(): Promise<void> {
  const matchId = selectedMatchId.value;
  const current = lineup.value;
  const formation = currentFormation.value;
  if (matchId === null || !current || !formation || saving.value || !canEdit.value) {
    return;
  }
  if (!opponent.value && starterCount.value !== 8) {
    actionError.value = '首发需排满 8 人后才能保存。';
    return;
  }
  saving.value = true;
  actionError.value = '';
  conflictMessage.value = '';
  try {
    const request: LineupSaveRequest = {
      formationTemplateId: formation.id,
      starters: formation.slots.map((slot) => ({
        slotKey: slot.slotKey,
        playerId: opponent.value ? null : (draftStarters.value[slot.slotKey] ?? null),
      })),
      bench: opponent.value ? [] : [...draftBench.value],
      version: current.version,
    };
    await api.put<LineupSaveResponse>(`/matches/${matchId}/lineups/${side.value}`, request, {
      idempotencyKey: newIdempotencyKey(),
    });
    if (matchId === selectedMatchId.value) {
      ElMessage.success('阵容已保存');
      await loadLineup(matchId, side.value);
    }
  } catch (caught) {
    if (matchId !== selectedMatchId.value) {
      return;
    }
    if (isApiError(caught) && caught.code === ERROR_CODES.VERSION_CONFLICT) {
      const data = caught.data as { currentVersion?: number } | undefined;
      conflictMessage.value = `阵容已被他人修改${
        data?.currentVersion !== undefined ? `（当前版本 ${data.currentVersion}）` : ''
      }，请刷新后重试。`;
    } else if (isApiError(caught) && caught.code === ERROR_CODES.MATCH_STATE_CONFLICT) {
      await loadLineup(matchId, side.value);
      actionError.value = caught.message;
    } else {
      actionError.value = isApiError(caught) ? caught.message : '保存阵容失败，请稍后重试';
    }
  } finally {
    saving.value = false;
  }
}

function refreshAfterConflict(): void {
  if (selectedMatchId.value !== null) {
    conflictMessage.value = '';
    void loadLineup(selectedMatchId.value, side.value);
  }
}

const templateDialog = reactive({
  visible: false,
  name: '',
  saving: false,
  error: '',
});

function openTemplateDialog(): void {
  if (!currentFormation.value || opponent.value) {
    return;
  }
  templateDialog.name = `${currentFormation.value.name} 变体`;
  templateDialog.error = '';
  templateDialog.visible = true;
}

async function submitTemplateDialog(): Promise<void> {
  const matchId = selectedMatchId.value;
  if (matchId === null || templateDialog.saving) {
    return;
  }
  const name = templateDialog.name.trim();
  if (!name) {
    templateDialog.error = '请输入模板名称';
    return;
  }
  templateDialog.saving = true;
  templateDialog.error = '';
  try {
    await api.post<LineupTemplateResponse>(`/matches/${matchId}/lineups/${side.value}/template`, {
      name,
    });
    templateDialog.visible = false;
    ElMessage.success('阵型已另存为模板');
    await loadTemplates();
  } catch (caught) {
    templateDialog.error = isApiError(caught) ? caught.message : '保存模板失败，请稍后重试';
  } finally {
    templateDialog.saving = false;
  }
}

// ---------------------------------------------------------------- templates

const copyDialog = reactive({
  visible: false,
  baseId: 0,
  baseName: '',
  name: '',
  saving: false,
  error: '',
});
const renameDialog = reactive({
  visible: false,
  id: 0,
  name: '',
  saving: false,
  error: '',
});

function openCopy(base: FormationTemplate): void {
  copyDialog.baseId = base.id;
  copyDialog.baseName = base.name;
  copyDialog.name = `${base.name} 变体`;
  copyDialog.error = '';
  copyDialog.visible = true;
}

async function submitCopy(): Promise<void> {
  if (copyDialog.saving) {
    return;
  }
  const name = copyDialog.name.trim();
  if (!name) {
    copyDialog.error = '请输入模板名称';
    return;
  }
  copyDialog.saving = true;
  copyDialog.error = '';
  try {
    const request: FormationTemplateCreateRequest = {
      baseTemplateId: copyDialog.baseId,
      name,
    };
    await api.post<FormationTemplateResponse>('/formation-templates', request);
    copyDialog.visible = false;
    ElMessage.success('阵型模板已创建');
    await loadTemplates();
  } catch (caught) {
    copyDialog.error = isApiError(caught) ? caught.message : '创建模板失败，请稍后重试';
  } finally {
    copyDialog.saving = false;
  }
}

function openRename(template: FormationTemplate): void {
  renameDialog.id = template.id;
  renameDialog.name = template.name;
  renameDialog.error = '';
  renameDialog.visible = true;
}

async function submitRename(): Promise<void> {
  if (renameDialog.saving) {
    return;
  }
  const name = renameDialog.name.trim();
  if (!name) {
    renameDialog.error = '请输入模板名称';
    return;
  }
  renameDialog.saving = true;
  renameDialog.error = '';
  try {
    const request: FormationTemplateRenameRequest = { name };
    await api.patch<FormationTemplateResponse>(`/formation-templates/${renameDialog.id}`, request);
    renameDialog.visible = false;
    ElMessage.success('阵型模板已重命名');
    await loadTemplates();
  } catch (caught) {
    renameDialog.error = isApiError(caught) ? caught.message : '重命名失败，请稍后重试';
  } finally {
    renameDialog.saving = false;
  }
}

async function removeTemplate(template: FormationTemplate): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `删除自定义模板“${template.name}”？历史阵容仍保留该模板引用。`,
      '删除阵型模板',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    );
  } catch {
    return;
  }
  try {
    await api.delete(`/formation-templates/${template.id}`);
    ElMessage.success('阵型模板已删除');
    await loadTemplates();
  } catch (caught) {
    if (isApiError(caught) && caught.code === ERROR_CODES.NOT_FOUND) {
      ElMessage.warning('模板已被删除，列表已刷新');
      await loadTemplates();
      return;
    }
    ElMessage.error(isApiError(caught) ? caught.message : '删除失败，请稍后重试');
  }
}

onMounted(async () => {
  await Promise.all([loadMatches(), loadTemplates()]);
});
</script>

<template>
  <section class="lineup-page">
    <div class="page-head">
      <div>
        <h2>阵容</h2>
        <p class="muted">选择比赛与主客队后排阵；队内/训练赛按已保存分组切队。</p>
      </div>
      <div class="head-actions">
        <el-select
          v-if="matches.length"
          v-model="selectedMatchId"
          class="match-picker"
          filterable
          placeholder="选择比赛"
          @change="onMatchChange"
        >
          <el-option
            v-for="match in matches"
            :key="match.id"
            :value="match.id"
            :label="`${match.title} · ${formatDateTime(match.startAt)} · ${MATCH_STATUS_TEXT[match.status]}`"
          />
        </el-select>
        <el-button :disabled="loadingMatches || loadingLineup" @click="refreshSelected">
          刷新
        </el-button>
      </div>
    </div>

    <el-alert
      v-if="!canView"
      type="info"
      :closable="false"
      show-icon
      title="当前账号没有查看阵容权限。"
    />
    <el-alert v-else-if="pageError" type="error" :title="pageError" :closable="false" show-icon>
      <el-button size="small" @click="loadMatches">重试</el-button>
    </el-alert>

    <div v-if="canView" v-loading="loadingMatches" class="board-body">
      <el-empty v-if="!loadingMatches && !matches.length" description="暂无比赛，请先创建比赛">
        <RouterLink to="/matches">
          <el-button type="primary">去比赛管理</el-button>
        </RouterLink>
      </el-empty>

      <template v-else-if="selectedMatch">
        <section class="toolbar card">
          <div class="toolbar-main">
            <div class="toolbar-title">
              <strong>{{ selectedMatch.title }}</strong>
              <el-tag size="small" effect="light">{{
                MATCH_STATUS_TEXT[selectedMatch.status]
              }}</el-tag>
              <el-tag v-if="opponent" size="small" type="info" effect="plain">对手方</el-tag>
              <el-tag v-if="dirty" size="small" type="warning" effect="plain">未保存</el-tag>
              <el-tag
                v-if="lineup"
                class="lineup-version"
                size="small"
                type="success"
                effect="plain"
              >
                v{{ lineup.version }}
              </el-tag>
            </div>
            <div class="toolbar-controls">
              <el-radio-group v-model="side" size="small" @change="onSideChange">
                <el-radio-button :value="0">{{ selectedMatch.homeTeamName }}</el-radio-button>
                <el-radio-button :value="1">{{ selectedMatch.awayTeamName }}</el-radio-button>
              </el-radio-group>
              <el-select
                v-model="draftFormationId"
                class="formation-picker"
                size="small"
                :disabled="!canEdit || !lineup"
                @change="onFormationChange"
              >
                <el-option
                  v-for="template in formationOptions"
                  :key="template.id"
                  :value="template.id"
                  :label="`${template.name}${template.isPreset ? '（预置）' : '（自定义）'}`"
                />
              </el-select>
            </div>
          </div>
          <div class="toolbar-actions">
            <el-button
              v-if="lineup && !opponent"
              size="small"
              :disabled="!canEdit || saving"
              @click="openTemplateDialog"
            >
              另存为模板
            </el-button>
            <el-button
              type="primary"
              size="small"
              :disabled="!canEdit || saving || Boolean(saveDisabledReason)"
              :loading="saving"
              @click="saveLineup"
            >
              保存阵容
            </el-button>
          </div>
        </section>

        <el-alert
          v-if="saveDisabledReason"
          type="info"
          :closable="false"
          show-icon
          :title="saveDisabledReason"
        />
        <el-alert
          v-if="actionError"
          type="error"
          :closable="false"
          show-icon
          :title="actionError"
        />
        <el-alert
          v-if="conflictMessage"
          type="warning"
          :closable="false"
          show-icon
          :title="conflictMessage"
        >
          <el-button size="small" @click="refreshAfterConflict">刷新后重试</el-button>
        </el-alert>
        <el-alert
          v-if="groupingChanged"
          type="warning"
          :closable="false"
          show-icon
          title="本场确认名单与已保存分组不一致；阵容只能使用分组内球员，请先到 B06 重新生成并保存分组。"
        >
          <p v-if="rosterDiffNames.added.length" class="small">
            新增：{{ rosterDiffNames.added.join('、') }}
          </p>
          <p v-if="rosterDiffNames.removed.length" class="small">
            移除：{{ rosterDiffNames.removed.join('、') }}
          </p>
        </el-alert>
        <el-alert
          v-if="!opponent && lineup && starterCount === 0 && canEdit"
          type="info"
          :closable="false"
          show-icon
          title="尚未安排首发：请从替补中拖拽或点选 8 名球员到球场位置。"
        />
        <el-alert
          v-if="opponent"
          type="info"
          :closable="false"
          show-icon
          title="对手方仅记录阵型与空槽位，不录入球员。"
        />

        <div v-loading="loadingLineup" class="board-area">
          <el-alert
            v-if="lineupError"
            type="error"
            :title="lineupError"
            :closable="false"
            show-icon
          >
            <el-button size="small" @click="loadLineup(selectedMatch.id, side)">重试</el-button>
          </el-alert>

          <template v-else-if="lineup && currentFormation">
            <div class="lineup-layout">
              <section class="pitch-panel card">
                <header class="panel-head">
                  <h3>
                    {{ currentFormation.name }} ·
                    {{ side === 0 ? selectedMatch.homeTeamName : selectedMatch.awayTeamName }}
                  </h3>
                  <span class="muted small">
                    {{ opponent ? '仅阵型与占位' : `已排 ${starterCount}/8` }}
                  </span>
                </header>
                <div class="pitch" @dragover="onDragOver" @drop="onDropBench">
                  <div class="pitch-line pitch-line--half" />
                  <div class="pitch-circle" />
                  <div
                    v-for="slot in currentFormation.slots"
                    :key="slot.slotKey"
                    class="pitch-slot"
                    :class="{
                      'pitch-slot--filled': starterOf(slot.slotKey),
                      'pitch-slot--selected':
                        selectedPlayer && draftStarters[slot.slotKey] === selectedPlayer.playerId,
                    }"
                    :style="{ left: `${slot.x}%`, top: `${slot.y}%` }"
                    :draggable="canEdit && !opponent && Boolean(starterOf(slot.slotKey))"
                    @click="onSlotClick(slot.slotKey)"
                    @dragstart="onDragStart($event, draftStarters[slot.slotKey] ?? 0)"
                    @dragover="onDragOver"
                    @drop="onDropSlot($event, slot.slotKey)"
                  >
                    <template v-if="starterOf(slot.slotKey)">
                      <span class="pitch-num">#{{ starterOf(slot.slotKey)?.jersey ?? '—' }}</span>
                      <span class="pitch-name">{{ starterOf(slot.slotKey)?.name }}</span>
                    </template>
                    <span v-else class="pitch-empty">{{ positionText(slot.role) }}</span>
                  </div>
                </div>
                <p class="muted small">
                  {{
                    opponent
                      ? '对手方仅记录阵型与占位槽位。'
                      : '点击替补球员再点球场位置安排；点击已占位置移回替补。'
                  }}
                </p>
              </section>

              <section class="bench-panel card" @dragover="onDragOver" @drop="onDropBench">
                <header class="panel-head">
                  <h3>替补</h3>
                  <span class="muted small">{{ benchPlayers.length }} 人</span>
                </header>
                <div v-if="benchPlayers.length" class="bench-list">
                  <div
                    v-for="player in benchPlayers"
                    :key="player.id"
                    class="bench-item bench-item--bench"
                    :class="{ 'bench-item--selected': selectedPlayer?.playerId === player.id }"
                    :draggable="canEdit && !opponent"
                    @dragstart="onDragStart($event, player.id)"
                  >
                    <button type="button" class="bench-select" @click="onBenchClick(player.id)">
                      <span class="bench-name">{{ player.name }}</span>
                      <small class="muted">
                        #{{ player.jersey ?? '—' }} · {{ positionText(player.position) }}
                      </small>
                    </button>
                    <el-button
                      v-if="canEdit && !opponent"
                      size="small"
                      text
                      @click.stop="removeFromBench(player.id)"
                    >
                      ×
                    </el-button>
                  </div>
                </div>
                <p v-else class="empty-inline">
                  {{ opponent ? '对手方不录入球员' : '暂无替补' }}
                </p>

                <template v-if="!opponent">
                  <header class="panel-head panel-head--sub">
                    <h3>未分配</h3>
                    <span class="muted small">{{ unassignedPlayers.length }} 人</span>
                  </header>
                  <div v-if="unassignedPlayers.length" class="bench-list">
                    <div
                      v-for="player in unassignedPlayers"
                      :key="player.id"
                      class="bench-item bench-item--unassigned"
                    >
                      <span class="bench-name">{{ player.name }}</span>
                      <small class="muted">
                        #{{ player.jersey ?? '—' }} · {{ positionText(player.position) }}
                      </small>
                      <el-button v-if="canEdit" size="small" text @click="addToBench(player.id)">
                        加入替补
                      </el-button>
                    </div>
                  </div>
                  <p v-else class="empty-inline">名单已全部分配</p>
                </template>
              </section>
            </div>

            <section v-if="internalMatch" v-loading="loadingGrouping" class="card roster-section">
              <header class="panel-head">
                <h3>本场报名与分组</h3>
                <span class="muted small">
                  {{ savedGrouping ? '已保存分组约束阵容' : '无已保存分组，使用确认报名' }}
                </span>
              </header>
              <p v-if="groupingError" class="hint hint--warning">{{ groupingError }}</p>
              <p v-if="rosterError" class="hint hint--warning">{{ rosterError }}</p>

              <div
                v-if="grouping && (grouping.teams.A.length || grouping.teams.B.length)"
                class="group-columns"
              >
                <section
                  v-for="team in ['A', 'B'] as const"
                  :key="team"
                  class="group-column"
                  :class="{ 'group-column--active': side === (team === 'A' ? 0 : 1) }"
                  @click="switchGroupingTeam(team)"
                >
                  <header class="group-column-head">
                    <strong>{{ team }} 队 · {{ groupingTeamLabel(team) }}</strong>
                    <span class="muted small">{{ grouping.teams[team].length }} 人</span>
                  </header>
                  <span
                    v-for="player in grouping.teams[team]"
                    :key="player.playerId"
                    class="player-chip"
                  >
                    {{ player.name }} #{{ player.jersey ?? '—' }}
                  </span>
                  <p v-if="!grouping.teams[team].length" class="empty-inline">暂无球员</p>
                </section>
              </div>

              <div v-else class="roster-list">
                <div v-for="row in confirmedRoster" :key="row.player.id" class="roster-row">
                  <span>{{ playerLabel(row.player) }}</span>
                  <el-tag size="small" type="success" effect="light">已确认</el-tag>
                </div>
                <p v-if="!confirmedRoster.length" class="empty-inline">本场暂无确认报名球员</p>
              </div>

              <div v-if="ungroupedPlayers.length" class="subsection">
                <h4>未分组（无法排入阵容）</h4>
                <span v-for="player in ungroupedPlayers" :key="player.id" class="player-chip">
                  {{ playerLabel(player) }}
                </span>
              </div>
              <div v-if="nonParticipating.length" class="subsection">
                <h4>不参与（外挂）</h4>
                <span v-for="player in nonParticipating" :key="player.id" class="player-chip">
                  {{ player.name }} #{{ player.jersey ?? '—' }}
                </span>
              </div>
            </section>

            <section v-else v-loading="loadingRoster" class="card roster-section">
              <header class="panel-head">
                <h3>本场确认报名</h3>
                <span class="muted small">{{ confirmedRoster.length }} 人</span>
              </header>
              <p v-if="rosterError" class="hint hint--warning">{{ rosterError }}</p>
              <div class="roster-list">
                <div v-for="row in confirmedRoster" :key="row.player.id" class="roster-row">
                  <span>{{ playerLabel(row.player) }}</span>
                  <el-tag size="small" type="success" effect="light">已确认</el-tag>
                </div>
                <p v-if="!confirmedRoster.length" class="empty-inline">本场暂无确认报名球员</p>
              </div>
            </section>
          </template>
        </div>
      </template>
    </div>

    <section v-if="canEdit" class="template-body">
      <header class="section-head">
        <h3>阵型模板</h3>
        <span class="muted small">预置模板只读；自定义模板可复制、重命名和删除。</span>
      </header>

      <section class="template-section">
        <header class="section-head">
          <h3>预置阵型</h3>
          <span class="muted small">{{ presets.length }} 套 · 只读</span>
        </header>
        <div v-if="presets.length" class="template-grid">
          <article
            v-for="template in presets"
            :key="template.id"
            class="template-card template-card--preset"
          >
            <header class="card-head">
              <strong>{{ template.name }}</strong>
              <el-tag size="small" type="info" effect="plain">预置</el-tag>
            </header>
            <div class="slot-preview" aria-hidden="true">
              <span
                v-for="slot in template.slots"
                :key="slot.slotKey"
                class="slot-dot"
                :style="{ left: `${slot.x}%`, top: `${slot.y}%` }"
              />
            </div>
            <p class="slot-summary">{{ slotSummary(template) }}</p>
            <div class="template-actions">
              <el-button type="primary" plain size="small" @click="openCopy(template)">
                复制并新建
              </el-button>
              <el-tooltip content="预置阵型不可重命名或删除，请复制为自定义模板后修改">
                <span class="preset-locked">
                  <el-button size="small" disabled>重命名</el-button>
                  <el-button size="small" disabled>删除</el-button>
                </span>
              </el-tooltip>
            </div>
            <p class="muted small preset-note">预置阵型只读，可复制后修改。</p>
          </article>
        </div>
        <el-empty v-else-if="!loadingTemplates" description="暂无预置阵型" />
      </section>

      <section class="template-section">
        <header class="section-head">
          <h3>自定义模板</h3>
          <span class="muted small">{{ customs.length }} 个</span>
        </header>
        <div v-if="customs.length" class="template-grid">
          <article
            v-for="template in customs"
            :key="template.id"
            class="template-card template-card--custom"
          >
            <header class="card-head">
              <strong>{{ template.name }}</strong>
              <el-tag size="small" type="success" effect="plain">自定义</el-tag>
            </header>
            <div class="slot-preview" aria-hidden="true">
              <span
                v-for="slot in template.slots"
                :key="slot.slotKey"
                class="slot-dot"
                :style="{ left: `${slot.x}%`, top: `${slot.y}%` }"
              />
            </div>
            <p class="slot-summary">{{ slotSummary(template) }}</p>
            <div class="template-actions">
              <el-button size="small" @click="openRename(template)">重命名</el-button>
              <el-button size="small" type="danger" plain @click="removeTemplate(template)">
                删除
              </el-button>
            </div>
          </article>
        </div>
        <el-empty
          v-else-if="!loadingTemplates"
          description="暂无自定义模板，可从预置阵型复制新建"
        />
      </section>
    </section>
    <el-alert
      v-else
      type="info"
      :closable="false"
      show-icon
      title="当前账号没有阵型模板编辑权限，模板管理不可用。"
    />

    <el-dialog v-model="templateDialog.visible" title="另存当前阵型为模板" width="420px">
      <p class="muted small">从当前已保存阵容的阵型复制；不会改变本场阵容版本。</p>
      <el-input
        v-model="templateDialog.name"
        maxlength="64"
        show-word-limit
        placeholder="模板名称"
        @keyup.enter="submitTemplateDialog"
      />
      <p v-if="templateDialog.error" class="dialog-error">{{ templateDialog.error }}</p>
      <template #footer>
        <el-button @click="templateDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="templateDialog.saving" @click="submitTemplateDialog">
          保存模板
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="copyDialog.visible" title="复制预置阵型" width="420px">
      <p class="muted small">来源：{{ copyDialog.baseName }}</p>
      <el-input
        v-model="copyDialog.name"
        maxlength="64"
        show-word-limit
        placeholder="模板名称"
        @keyup.enter="submitCopy"
      />
      <p v-if="copyDialog.error" class="dialog-error">{{ copyDialog.error }}</p>
      <template #footer>
        <el-button @click="copyDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="copyDialog.saving" @click="submitCopy">
          创建模板
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="renameDialog.visible" title="重命名自定义模板" width="420px">
      <el-input
        v-model="renameDialog.name"
        maxlength="64"
        show-word-limit
        placeholder="新的模板名称"
        @keyup.enter="submitRename"
      />
      <p v-if="renameDialog.error" class="dialog-error">{{ renameDialog.error }}</p>
      <template #footer>
        <el-button @click="renameDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="renameDialog.saving" @click="submitRename">
          保存名称
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.lineup-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.page-head h2 {
  margin: 0 0 4px;
  font-size: 20px;
}

.page-head p {
  margin: 0;
  font-size: 13px;
}

.head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.match-picker {
  width: 360px;
  max-width: 100%;
}

.board-body {
  min-height: 120px;
}

.card {
  padding: 16px;
  background: var(--yh-surface);
  border: 1px solid var(--yh-line);
  border-radius: 12px;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.toolbar-main {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.toolbar-title,
.toolbar-controls,
.toolbar-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.formation-picker {
  width: 180px;
}

.board-area {
  min-height: 200px;
}

.lineup-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(240px, 0.8fr);
  gap: 14px;
}

.panel-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.panel-head h3 {
  margin: 0;
  font-size: 15px;
}

.panel-head--sub {
  margin-top: 18px;
}

.pitch {
  position: relative;
  width: 100%;
  aspect-ratio: 3 / 4;
  max-height: 460px;
  margin: 0 auto;
  overflow: hidden;
  background:
    linear-gradient(0deg, rgba(255, 255, 255, 0.07) 1px, transparent 1px) 0 0 / 100% 20%,
    linear-gradient(90deg, #166534, #15803d);
  border-radius: 12px;
}

.pitch-line--half {
  position: absolute;
  top: 50%;
  right: 8%;
  left: 8%;
  height: 1px;
  background: rgba(255, 255, 255, 0.45);
}

.pitch-circle {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 26%;
  aspect-ratio: 1;
  border: 1px solid rgba(255, 255, 255, 0.45);
  border-radius: 50%;
  transform: translate(-50%, -50%);
}

.pitch-slot {
  position: absolute;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 44px;
  padding: 2px;
  font-size: 11px;
  color: #f8fafc;
  cursor: pointer;
  background: rgba(15, 23, 42, 0.55);
  border: 1px dashed rgba(255, 255, 255, 0.65);
  border-radius: 8px;
  transform: translate(-50%, -50%);
}

.pitch-slot--filled {
  background: #f8fafc;
  border-style: solid;
  border-color: #0f172a;
  color: #0f172a;
}

.pitch-slot--selected {
  box-shadow: 0 0 0 3px #fbbf24;
}

.pitch-num {
  font-weight: 700;
}

.pitch-name {
  max-width: 52px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pitch-empty {
  color: rgba(248, 250, 252, 0.85);
}

.bench-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 360px;
  overflow-y: auto;
}

.bench-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 9px;
  font-size: 12px;
  background: #f8fafc;
  border: 1px solid var(--yh-line);
  border-radius: 9px;
  cursor: pointer;
}

.bench-item--selected {
  background: #eff6ff;
  border-color: var(--el-color-primary);
}

.bench-select {
  display: flex;
  flex: 1;
  gap: 8px;
  align-items: center;
  min-width: 0;
  padding: 0;
  font: inherit;
  color: inherit;
  text-align: left;
  cursor: pointer;
  background: none;
  border: 0;
}

.bench-name {
  font-weight: 600;
}

.roster-section {
  margin-top: 14px;
}

.group-columns {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.group-column {
  min-height: 100px;
  padding: 12px;
  cursor: pointer;
  border: 1px solid var(--yh-line);
  border-radius: 10px;
}

.group-column--active {
  border-color: var(--el-color-primary);
  box-shadow: inset 0 0 0 1px var(--el-color-primary);
}

.group-column-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.player-chip {
  display: inline-block;
  padding: 4px 8px;
  margin: 0 6px 6px 0;
  font-size: 12px;
  background: #f8fafc;
  border: 1px solid var(--yh-line);
  border-radius: 999px;
}

.subsection {
  margin-top: 12px;
}

.subsection h4 {
  margin: 0 0 6px;
  font-size: 13px;
}

.roster-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.roster-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 0;
  font-size: 12px;
  border-bottom: 1px solid var(--yh-line);
}

.small {
  font-size: 12px;
}

.hint {
  margin: 8px 0;
  color: var(--yh-muted);
  font-size: 12px;
}

.hint--warning {
  color: var(--el-color-warning);
}

.empty-inline {
  margin: 8px 0;
  color: var(--yh-muted);
  font-size: 12px;
}

.template-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding-top: 4px;
  border-top: 1px solid var(--yh-line);
}

.template-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.section-head h3 {
  margin: 0;
  font-size: 15px;
}

.template-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(230px, 1fr));
  gap: 12px;
}

.template-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px;
  background: var(--yh-surface);
  border: 1px solid var(--yh-line);
  border-radius: 12px;
}

.template-card--custom {
  border-color: #b7dccb;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.card-head strong {
  font-size: 15px;
}

.slot-preview {
  position: relative;
  height: 110px;
  overflow: hidden;
  background:
    linear-gradient(0deg, rgba(255, 255, 255, 0.08) 1px, transparent 1px) 0 0 / 100% 25%,
    linear-gradient(90deg, #166534, #15803d);
  border-radius: 9px;
}

.slot-preview::after {
  position: absolute;
  right: 8%;
  left: 8%;
  height: 1px;
  content: '';
  background: rgba(255, 255, 255, 0.4);
  top: 50%;
}

.slot-dot {
  position: absolute;
  z-index: 1;
  width: 10px;
  height: 10px;
  background: #f8fafc;
  border: 2px solid #0f172a;
  border-radius: 50%;
  transform: translate(-50%, -50%);
}

.slot-summary {
  margin: 0;
  color: var(--yh-muted);
  font-size: 12px;
}

.template-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.template-actions .el-button + .el-button {
  margin-left: 0;
}

.preset-locked {
  display: inline-flex;
  gap: 8px;
}

.preset-note {
  margin: 0;
}

.muted {
  color: var(--yh-muted);
}

.dialog-error {
  margin: 8px 0 0;
  color: var(--el-color-danger);
  font-size: 12px;
}

@media (max-width: 960px) {
  .lineup-layout {
    grid-template-columns: 1fr;
  }

  .group-columns {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .page-head,
  .toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .match-picker {
    width: 100%;
  }
}
</style>
