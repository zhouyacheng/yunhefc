import {
  cancelReasonError,
  isoToShanghaiLocal,
  postponeFormError,
  shanghaiLocalToIso,
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

function splitLocal(iso: string): [string, string] {
  const local = isoToShanghaiLocal(iso) || isoToShanghaiLocal(new Date().toISOString());
  const [date, time] = local.split('T');
  return [date, time ?? '00:00'];
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
      const [startDate, startTime] = splitLocal(this.data.initialStartAt);
      const [endDate, endTime] = splitLocal(this.data.initialEndAt);
      const current = (this.data.options as StatusOption[]).find(
        (option) => option.status === this.data.currentStatus,
      );
      this.setData({
        mode: 'list',
        currentLabel: current?.label ?? '—',
        startDate,
        startTime,
        endDate,
        endTime,
        reason: '',
        formError: '',
      });
    },
    'currentStatus, options'() {
      const current = (this.data.options as StatusOption[]).find(
        (option) => option.status === this.data.currentStatus,
      );
      if (this.data.show && current) {
        this.setData({ currentLabel: current.label });
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
      const index = Number(event.currentTarget.dataset.index);
      const options = this.data.options as StatusOption[];
      const option = options[index];
      if (!option || option.disabled || option.status === this.data.currentStatus) {
        return;
      }
      if (option.status === 5) {
        this.setData({ mode: 'postpone', formError: '' });
        return;
      }
      if (option.status === 6) {
        this.setData({ mode: 'cancel', formError: '' });
        return;
      }
      this.triggerEvent('change', { status: option.status });
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
      const startLocal = `${this.data.startDate}T${this.data.startTime}`;
      const endLocal = `${this.data.endDate}T${this.data.endTime}`;
      const message = postponeFormError(startLocal, endLocal);
      if (message) {
        this.setData({ formError: message });
        return;
      }
      const startAt = shanghaiLocalToIso(startLocal);
      const endAt = shanghaiLocalToIso(endLocal);
      if (!startAt || !endAt) {
        this.setData({ formError: '请选择开始与结束时间' });
        return;
      }
      this.setData({ formError: '' });
      this.triggerEvent('postpone', { startAt, endAt });
    },

    onReason(event: WechatMiniprogram.CustomEvent): void {
      this.setData({ reason: String(event.detail.value) });
    },

    submitCancel(): void {
      const message = cancelReasonError(this.data.reason);
      if (message) {
        this.setData({ formError: message });
        return;
      }
      this.setData({ formError: '' });
      this.triggerEvent('cancel', { reason: this.data.reason.trim() });
    },
  },
});
