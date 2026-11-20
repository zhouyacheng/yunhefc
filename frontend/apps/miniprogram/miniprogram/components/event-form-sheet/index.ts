import type { EventFormPlayer, GroupingView, MatchSummary } from '../../types/api';
import {
  allowedEventTypes,
  defaultEventTeam,
  eventPayload,
  eventTeamRoster,
  teamLockedFor,
  validateEventForm,
  type EventFormState,
} from '../../utils/event-form';
import { EVENT_TYPE_TEXT, positionText } from '../../utils/presentation';

interface PlayerOption {
  id: number | null;
  label: string;
}

interface ValueOption {
  value: number;
  label: string;
}

function playerLabel(player: EventFormPlayer): string {
  const jersey = player.jersey === null || player.jersey === undefined ? '—' : player.jersey;
  return `${player.name} #${jersey} · ${positionText(player.position ?? -1)}`;
}

function optionsOf(players: EventFormPlayer[], emptyLabel: string): PlayerOption[] {
  return [
    { id: null, label: emptyLabel },
    ...players.map((player) => ({ id: player.id, label: playerLabel(player) })),
  ];
}

function indexOfPlayer(options: PlayerOption[], id: number | null | undefined): number {
  if (id === null || id === undefined) {
    return 0;
  }
  const index = options.findIndex((option) => option.id === id);
  return index >= 0 ? index : 0;
}

Component({
  properties: {
    show: { type: Boolean, value: false },
    match: { type: Object },
    players: { type: Array, value: [] as EventFormPlayer[] },
    grouping: { type: Object },
    editing: { type: Object },
    isAdmin: { type: Boolean, value: false },
    ownPlayerId: { type: Number, value: 0 },
    version: { type: Number, value: 0 },
    busy: { type: Boolean, value: false },
    error: { type: String, value: '' },
  },

  data: {
    typeOptions: [
      { value: 0, label: '进球' },
      { value: 1, label: '乌龙球' },
      { value: 3, label: '黄牌' },
      { value: 4, label: '红牌' },
    ] as ValueOption[],
    typeIndex: 0,
    teamOptions: [
      { value: 0, label: '主队' },
      { value: 1, label: '客队' },
    ] as ValueOption[],
    teamIndex: 0,
    teamLocked: false,
    playerOptions: [{ id: null, label: '请选择' }] as PlayerOption[],
    playerIndex: 0,
    assistOptions: [{ id: null, label: '无' }] as PlayerOption[],
    assistIndex: 0,
    inOptions: [{ id: null, label: '请选择' }] as PlayerOption[],
    inIndex: 0,
    outOptions: [{ id: null, label: '请选择' }] as PlayerOption[],
    outIndex: 0,
    halfOptions: [
      { value: 0, label: '上半场' },
      { value: 1, label: '下半场' },
    ] as ValueOption[],
    halfIndex: 0,
    minute: '',
    penalty: false,
    formError: '',
  },

  observers: {
    'show, editing'() {
      if (this.data.show) {
        this.reset();
      }
    },
  },

  methods: {
    reset(): void {
      const match = this.data.match as MatchSummary | null;
      const editing = this.data.editing as {
        type: number;
        team: number;
        player?: { id: number } | null;
        assistPlayer?: { id: number } | null;
        inPlayer?: { id: number } | null;
        outPlayer?: { id: number } | null;
        minute: number;
        half: number;
        penalty: boolean;
      } | null;
      const isAdmin = Boolean(this.data.isAdmin);
      const ownPlayerId = Number(this.data.ownPlayerId) || null;
      const grouping = (this.data.grouping as GroupingView | null) ?? null;
      const types = allowedEventTypes(isAdmin);
      const typeOptions = types.map((value) => ({ value, label: EVENT_TYPE_TEXT[value] }));
      const team = editing?.team ?? defaultEventTeam(match, grouping, ownPlayerId, isAdmin);
      const teamLocked = teamLockedFor(match, isAdmin);
      this.setData({
        typeOptions,
        typeIndex: Math.max(
          0,
          typeOptions.findIndex((option) => option.value === (editing?.type ?? 0)),
        ),
        teamOptions: [
          { value: 0, label: match?.homeTeamName ?? '主队' },
          { value: 1, label: match?.awayTeamName ?? '客队' },
        ],
        teamIndex: team === 1 ? 1 : 0,
        teamLocked,
        halfIndex: editing?.half === 1 ? 1 : 0,
        minute: editing ? String(editing.minute) : '',
        penalty: Boolean(editing?.penalty),
        formError: '',
        ...this.playerOptionsFor(team, {
          playerId: editing?.player?.id ?? null,
          assistPlayerId: editing?.assistPlayer?.id ?? null,
          inPlayerId: editing?.inPlayer?.id ?? null,
          outPlayerId: editing?.outPlayer?.id ?? null,
        }),
      });
    },

    playerOptionsFor(
      team: number,
      selected: {
        playerId: number | null;
        assistPlayerId: number | null;
        inPlayerId: number | null;
        outPlayerId: number | null;
      },
    ): Record<string, unknown> {
      const confirmed = (this.data.players as EventFormPlayer[] | undefined) ?? [];
      const ownPlayerId = Number(this.data.ownPlayerId) || null;
      const match = this.data.match as MatchSummary | null;
      const grouping = (this.data.grouping as GroupingView | null) ?? null;
      const teamPlayers = eventTeamRoster({ match, team, confirmed, grouping });
      const locked = teamLockedFor(match, Boolean(this.data.isAdmin));
      const pickable = locked
        ? teamPlayers.filter((player) => player.id === ownPlayerId)
        : teamPlayers;
      const teamAll = optionsOf(teamPlayers, '请选择');
      const playerOptions = optionsOf(pickable, '请选择');
      const assistOptions = optionsOf(teamPlayers, '无');
      return {
        playerOptions,
        playerIndex: indexOfPlayer(playerOptions, selected.playerId),
        assistOptions,
        assistIndex: indexOfPlayer(assistOptions, selected.assistPlayerId),
        inOptions: teamAll,
        inIndex: indexOfPlayer(teamAll, selected.inPlayerId),
        outOptions: teamAll,
        outIndex: indexOfPlayer(teamAll, selected.outPlayerId),
      };
    },

    onType(event: WechatMiniprogram.CustomEvent): void {
      this.setData({ typeIndex: Number(event.detail.value) });
    },

    onTeam(event: WechatMiniprogram.CustomEvent): void {
      const teamIndex = Number(event.detail.value);
      this.setData({
        teamIndex,
        ...this.playerOptionsFor(teamIndex, {
          playerId: null,
          assistPlayerId: null,
          inPlayerId: null,
          outPlayerId: null,
        }),
      });
    },

    onPlayer(event: WechatMiniprogram.CustomEvent): void {
      this.setData({ playerIndex: Number(event.detail.value) });
    },
    onAssist(event: WechatMiniprogram.CustomEvent): void {
      this.setData({ assistIndex: Number(event.detail.value) });
    },
    onIn(event: WechatMiniprogram.CustomEvent): void {
      this.setData({ inIndex: Number(event.detail.value) });
    },
    onOut(event: WechatMiniprogram.CustomEvent): void {
      this.setData({ outIndex: Number(event.detail.value) });
    },
    onHalf(event: WechatMiniprogram.CustomEvent): void {
      this.setData({ halfIndex: Number(event.detail.value) });
    },
    onMinute(event: WechatMiniprogram.CustomEvent): void {
      this.setData({ minute: String(event.detail.value) });
    },
    onPenalty(event: WechatMiniprogram.CustomEvent): void {
      this.setData({ penalty: Boolean(event.detail.value) });
    },

    submit(): void {
      if (this.data.busy) {
        return;
      }
      const typeOptions = this.data.typeOptions as ValueOption[];
      const playerOptions = this.data.playerOptions as PlayerOption[];
      const assistOptions = this.data.assistOptions as PlayerOption[];
      const inOptions = this.data.inOptions as PlayerOption[];
      const outOptions = this.data.outOptions as PlayerOption[];
      const minute = this.data.minute ? Number(this.data.minute) : null;
      const state: EventFormState = {
        type: typeOptions[this.data.typeIndex]?.value ?? 0,
        team: this.data.teamIndex === 1 ? 1 : 0,
        playerId: playerOptions[this.data.playerIndex]?.id ?? null,
        assistPlayerId: assistOptions[this.data.assistIndex]?.id ?? null,
        inPlayerId: inOptions[this.data.inIndex]?.id ?? null,
        outPlayerId: outOptions[this.data.outIndex]?.id ?? null,
        minute,
        half: this.data.halfIndex === 1 ? 1 : 0,
        penalty: this.data.penalty,
      };
      const message = validateEventForm(state);
      if (message) {
        this.setData({ formError: message });
        return;
      }
      const editing = this.data.editing as { id: number; version: number } | null;
      const version = Number(this.data.version) || editing?.version || null;
      this.setData({ formError: '' });
      this.triggerEvent('save', {
        payload: eventPayload(state),
        eventId: editing?.id ?? null,
        version,
      });
    },

    close(): void {
      this.triggerEvent('close');
    },
  },
});
