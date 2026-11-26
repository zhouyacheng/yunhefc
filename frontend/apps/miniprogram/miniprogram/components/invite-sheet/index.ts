import type { InviteCreated } from '../../types/api';
import { formatDateTimeWithYear } from '../../utils/datetime';
import type { ValueEvent } from '../../utils/input';
import {
  DEFAULT_INVITE_DAYS,
  inviteFormError,
  invitePayload,
  type InviteForm,
} from '../../utils/invite';

interface InviteSheetData {
  days: string;
  maxUses: string;
  formError: string;
  copied: boolean;
  expiryText: string;
}

Component({
  properties: {
    show: { type: Boolean, value: false },
    busy: { type: Boolean, value: false },
    created: { type: Object },
    error: { type: String, value: '' },
  },

  data: {
    days: String(DEFAULT_INVITE_DAYS),
    maxUses: '',
    formError: '',
    copied: false,
    expiryText: '',
  } as InviteSheetData,

  observers: {
    show(value: boolean) {
      if (!value) {
        return;
      }
      if (!this.currentCreated()) {
        this.setData({ days: String(DEFAULT_INVITE_DAYS), maxUses: '' });
      }
      this.setData({ formError: '', copied: false });
    },
    created(value: InviteCreated | null) {
      this.setData({
        copied: false,
        expiryText: value ? formatDateTimeWithYear(value.expiresAt) || '—' : '',
      });
    },
  },

  methods: {
    currentCreated(): InviteCreated | null {
      return (this.data.created as InviteCreated | null) ?? null;
    },

    onDaysInput(event: ValueEvent): void {
      this.setData({ days: event.detail.value });
    },

    onMaxUsesInput(event: ValueEvent): void {
      this.setData({ maxUses: event.detail.value });
    },

    submit(): void {
      if (this.data.busy) {
        return;
      }
      const form: InviteForm = {
        expiresInDays: this.data.days,
        maxUses: this.data.maxUses,
      };
      const message = inviteFormError(form);
      if (message) {
        this.setData({ formError: message });
        return;
      }
      this.setData({ formError: '' });
      this.triggerEvent('submit', invitePayload(form));
    },

    copy(): void {
      const created = this.currentCreated();
      if (!created) {
        return;
      }
      void wx.setClipboardData({
        data: created.inviteUrl,
        success: () => this.setData({ copied: true }),
        fail: () => wx.showToast({ title: '复制失败，请重试', icon: 'none' }),
      });
    },

    close(): void {
      if (this.data.busy) {
        return;
      }
      this.triggerEvent('close');
    },
  },
});
