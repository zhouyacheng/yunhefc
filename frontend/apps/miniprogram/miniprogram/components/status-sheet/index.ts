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
      const submission = buildCancelSubmission(this.data.reason);
      if (!submission.ok) {
        this.setData({ formError: submission.error });
        return;
      }
      this.setData({ formError: '' });
      this.triggerEvent('cancel', { reason: submission.reason });
    },
  },
});
