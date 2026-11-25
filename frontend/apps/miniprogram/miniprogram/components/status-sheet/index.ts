import {
  buildCancelSubmission,
  buildPostponeSubmission,
  statusChoice,
  statusCurrentLabel,
  statusSheetInitial,
  type StatusOption,
} from '../../utils/match-status';

type Mode = 'list' | 'postpone' | 'cancel';

interface StatusSheetData {
  mode: Mode;
  startDate: string;
  startTime: string;
  endDate: string;
  endTime: string;
  reason: string;
  formError: string;
}

Component({
  properties: {
    show: { type: Boolean, value: false },
    options: { type: Array, value: [] as StatusOption[] },
    currentStatus: { type: Number, value: 0 },
    error: { type: String, value: '' },
    initialStartAt: { type: String, value: '' },
    initialEndAt: { type: String, value: '' },
    /** 实体名，用于标题：比赛 / 活动。 */
    entityLabel: { type: String, value: '比赛' },
    /** 视为「取消」的状态码；活动为 3。 */
    cancelStatus: { type: Number, value: 6 },
    /** 取消原因上限；活动为 255。 */
    cancelMaxLength: { type: Number, value: 200 },
    /** 取消原因是否必填；活动设为 false。 */
    cancelReasonRequired: { type: Boolean, value: true },
    /** 列表态提示；空则使用比赛默认文案。 */
    listHint: { type: String, value: '' },
  },

  data: {
    mode: 'list',
    currentLabel: '—',
    startDate: '',
    startTime: '00:00',
    endDate: '',
    endTime: '00:00',
    reason: '',
    formError: '',
  } as StatusSheetData & { currentLabel: string },

  observers: {
    show(value: boolean) {
      if (!value) {
        return;
      }
      this.setData({
        ...statusSheetInitial({
          initialStartAt: this.data.initialStartAt,
          initialEndAt: this.data.initialEndAt,
          options: this.data.options as StatusOption[],
          currentStatus: this.data.currentStatus,
        }),
        reason: '',
        formError: '',
      });
    },
    'currentStatus, options'() {
      if (!this.data.show) {
        return;
      }
      const label = statusCurrentLabel(
        this.data.options as StatusOption[],
        this.data.currentStatus,
      );
      if (label !== null) {
        this.setData({ currentLabel: label });
      }
    },
  },

  methods: {
    close(): void {
      this.triggerEvent('close');
    },

    backToList(): void {
      this.setData({ mode: 'list', formError: '' });
    },

    choose(event: WechatMiniprogram.BaseEvent): void {
      const choice = statusChoice(
        this.data.options as StatusOption[],
        this.data.currentStatus,
        Number(event.currentTarget.dataset.index),
        this.data.cancelStatus,
      );
      switch (choice.kind) {
        case 'ignore':
          return;
        case 'postpone':
          this.setData({ mode: 'postpone', formError: '' });
          return;
        case 'cancel':
          this.setData({ mode: 'cancel', formError: '' });
          return;
        case 'change':
          this.triggerEvent('change', { status: choice.status });
          return;
      }
    },

    onStartDate(event: WechatMiniprogram.CustomEvent): void {
      this.setData({ startDate: String(event.detail.value) });
    },
    onStartTime(event: WechatMiniprogram.CustomEvent): void {
      this.setData({ startTime: String(event.detail.value) });
    },
    onEndDate(event: WechatMiniprogram.CustomEvent): void {
      this.setData({ endDate: String(event.detail.value) });
    },
    onEndTime(event: WechatMiniprogram.CustomEvent): void {
      this.setData({ endTime: String(event.detail.value) });
    },

    submitPostpone(): void {
      const submission = buildPostponeSubmission({
        startDate: this.data.startDate,
        startTime: this.data.startTime,
        endDate: this.data.endDate,
        endTime: this.data.endTime,
      });
      if (!submission.ok) {
        this.setData({ formError: submission.error });
        return;
      }
      this.setData({ formError: '' });
      this.triggerEvent('postpone', { startAt: submission.startAt, endAt: submission.endAt });
    },

    onReason(event: WechatMiniprogram.CustomEvent): void {
      this.setData({ reason: String(event.detail.value) });
    },

    submitCancel(): void {
      const submission = buildCancelSubmission(
        this.data.reason,
        this.data.cancelMaxLength,
        this.data.cancelReasonRequired,
      );
      if (!submission.ok) {
        this.setData({ formError: submission.error });
        return;
      }
      this.setData({ formError: '' });
      this.triggerEvent('cancel', { reason: submission.reason });
    },
  },
});
