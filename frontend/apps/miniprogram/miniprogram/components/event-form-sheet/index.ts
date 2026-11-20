import type { EventFormPlayer, GroupingView, MatchSummary } from '../../types/api';
import {
  buildEventSubmission,
  eventFormView,
  eventTeamPickers,
  type EventFormEditing,
  type PlayerOption,
  type ValueOption,
} from '../../utils/event-form';

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
    typeOptions: [] as ValueOption[],
    typeIndex: 0,
    teamOptions: [] as ValueOption[],
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
      this.setData({
        ...eventFormView({
          match: this.data.match as MatchSummary | null,
          players: (this.data.players as EventFormPlayer[] | undefined) ?? [],
          grouping: (this.data.grouping as GroupingView | null) ?? null,
          editing: (this.data.editing as EventFormEditing | null) ?? null,
          isAdmin: Boolean(this.data.isAdmin),
          ownPlayerId: Number(this.data.ownPlayerId) || null,
        }),
        formError: '',
      });
    },

    onType(event: WechatMiniprogram.CustomEvent): void {
      this.setData({ typeIndex: Number(event.detail.value) });
    },

    onTeam(event: WechatMiniprogram.CustomEvent): void {
      const teamIndex = Number(event.detail.value);
      this.setData({
        teamIndex,
        ...eventTeamPickers({
          match: this.data.match as MatchSummary | null,
          players: (this.data.players as EventFormPlayer[] | undefined) ?? [],
          grouping: (this.data.grouping as GroupingView | null) ?? null,
          isAdmin: Boolean(this.data.isAdmin),
          ownPlayerId: Number(this.data.ownPlayerId) || null,
          team: teamIndex,
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
      const submission = buildEventSubmission({
        typeOptions: this.data.typeOptions as ValueOption[],
        playerOptions: this.data.playerOptions as PlayerOption[],
        assistOptions: this.data.assistOptions as PlayerOption[],
        inOptions: this.data.inOptions as PlayerOption[],
        outOptions: this.data.outOptions as PlayerOption[],
        typeIndex: this.data.typeIndex,
        teamIndex: this.data.teamIndex,
        playerIndex: this.data.playerIndex,
        assistIndex: this.data.assistIndex,
        inIndex: this.data.inIndex,
        outIndex: this.data.outIndex,
        halfIndex: this.data.halfIndex,
        minute: this.data.minute,
        penalty: this.data.penalty,
        version: Number(this.data.version),
        editing: (this.data.editing as { id: number; version: number } | null) ?? null,
      });
      if (!submission.ok) {
        this.setData({ formError: submission.error });
        return;
      }
      this.setData({ formError: '' });
      this.triggerEvent('save', {
        payload: submission.payload,
        eventId: submission.eventId,
        version: submission.version,
      });
    },

    close(): void {
      this.triggerEvent('close');
    },
  },
});
