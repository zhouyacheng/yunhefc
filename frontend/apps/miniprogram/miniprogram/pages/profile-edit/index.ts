import type { AvatarUploadView, PlayerPublic, PlayerView } from '../../types/api';
import {
  buildOptions,
  MAX_SECONDARY,
  POSITION_CODES,
  POSITION_LABELS,
  validateAvatar,
  validateProfile,
  withoutPrimary,
} from '../../utils/profile-edit';
import { ensureCsrf, request, uploadFile } from '../../utils/request';
import { getPlayer, getUser, setPlayer } from '../../utils/session';

interface TeamPlayersView {
  items: PlayerPublic[];
  total: number;
}

interface ValueEvent {
  detail: { value: string };
}

Page({
  data: {
    loading: true,
    name: '',
    initial: '云',
    region: '',
    position: 0,
    positionIndex: 0,
    positionLabels: POSITION_LABELS,
    secondaryOptions: buildOptions(0, []),
    avatarPreview: '',
    avatarObjectKey: '',
    uploading: false,
    saving: false,
    warning: '',
    error: '',
  },

  onLoad() {
    const user = getUser();
    if (!user) {
      wx.showToast({ title: '请先登录', icon: 'none' });
      void wx.navigateBack();
      return;
    }
    if (user.status === 0) {
      wx.showToast({ title: '账号待审核，暂不能编辑资料', icon: 'none' });
      void wx.navigateBack();
      return;
    }
    void this.load();
  },

  async load() {
    const player = getPlayer();
    if (!player) {
      this.setData({ loading: false, error: '未找到球员资料' });
      return;
    }
    this.setData({
      loading: true,
      name: player.name,
      initial: player.name.slice(0, 1) || '云',
      region: player.region ?? '',
      position: player.position,
      positionIndex: Math.max(0, POSITION_CODES.indexOf(player.position)),
      secondaryOptions: buildOptions(
        player.position,
        withoutPrimary(player.secondaryPositions ?? [], player.position),
      ),
      avatarPreview: player.avatarUrl ?? '',
      warning: '',
      error: '',
    });

    try {
      const view = await request<TeamPlayersView>('GET', '/team/players?all=true');
      const self = view.items.find((item) => item.id === player.id);
      if (!self) {
        this.setData({ warning: '未能加载完整资料，请重新填写地区与副位置' });
        return;
      }
      const secondary = withoutPrimary(
        self.secondaryPositions ?? [],
        player.position,
      ).slice(0, MAX_SECONDARY);
      this.setData({
        region: self.region ?? '',
        secondaryOptions: buildOptions(player.position, secondary),
      });
    } catch (error) {
      this.setData({
        warning: '资料加载失败，请重新填写地区与副位置',
        error: (error as Error).message || '',
      });
    } finally {
      this.setData({ loading: false });
    }
  },

  onNameInput(event: ValueEvent) {
    const name = event.detail.value;
    this.setData({ name, initial: name.slice(0, 1) || '云' });
  },

  onRegionInput(event: ValueEvent) {
    this.setData({ region: event.detail.value });
  },

  onPositionChange(event: ValueEvent) {
    const position = POSITION_CODES[Number(event.detail.value)] ?? 0;
    const selected = this.data.secondaryOptions
      .filter((option) => option.checked && option.code !== position)
      .map((option) => option.code);
    this.setData({
      position,
      positionIndex: Number(event.detail.value),
      secondaryOptions: buildOptions(position, selected),
    });
  },

  toggleSecondary(event: WechatMiniprogram.BaseEvent) {
    const code = Number(event.currentTarget.dataset.code);
    const option = this.data.secondaryOptions.find((item) => item.code === code);
    if (!option || option.disabled) {
      return;
    }
    const selected = this.data.secondaryOptions
      .filter((item) => item.checked)
      .map((item) => item.code);
    const next = selected.includes(code)
      ? selected.filter((item) => item !== code)
      : [...selected, code];
    if (next.length > MAX_SECONDARY) {
      wx.showToast({ title: `副位置最多 ${MAX_SECONDARY} 个`, icon: 'none' });
      return;
    }
    this.setData({ secondaryOptions: buildOptions(this.data.position, next) });
  },

  chooseAvatar() {
    if (this.data.uploading) {
      return;
    }
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sizeType: ['compressed'],
      sourceType: ['album', 'camera'],
      success: (res) => {
        const file = res.tempFiles[0];
        if (!file) {
          return;
        }
        const invalid = validateAvatar(file.size, file.tempFilePath);
        if (invalid) {
          this.setData({ error: invalid });
          return;
        }
        void this.upload(file.tempFilePath);
      },
      fail: (error) => {
        if (error.errMsg && error.errMsg.includes('cancel')) {
          return;
        }
        this.setData({ error: '选择图片失败，请重试' });
      },
    });
  },

  async upload(filePath: string) {
    this.setData({ uploading: true, error: '' });
    try {
      await ensureCsrf();
      const uploaded = await uploadFile<AvatarUploadView>('/files/avatar', filePath);
      this.setData({
        avatarPreview: uploaded.signedUrl,
        avatarObjectKey: uploaded.objectKey,
      });
    } catch (error) {
      this.setData({ error: (error as Error).message || '头像上传失败，请重试' });
    } finally {
      this.setData({ uploading: false });
    }
  },

  async save() {
    if (this.data.saving || this.data.uploading) {
      return;
    }
    const name = this.data.name.trim();
    const region = this.data.region.trim();
    const invalid = validateProfile(name, region);
    if (invalid) {
      this.setData({ error: invalid });
      return;
    }

    const secondaryPositions = this.data.secondaryOptions
      .filter((option) => option.checked)
      .map((option) => option.code)
      .sort((a, b) => a - b);

    this.setData({ saving: true, error: '' });
    try {
      await ensureCsrf();
      const payload: {
        name: string;
        region: string;
        position: number;
        secondaryPositions: number[];
        avatarUrl?: string;
      } = {
        name,
        region,
        position: this.data.position,
        secondaryPositions,
      };
      if (this.data.avatarObjectKey) {
        payload.avatarUrl = this.data.avatarObjectKey;
      }
      const saved = await request<PlayerView>('PATCH', '/me/player', payload);
      setPlayer(saved);
      wx.showToast({ title: '资料已保存', icon: 'none' });
      void wx.navigateBack();
    } catch (error) {
      this.setData({ error: (error as Error).message || '保存失败，请稍后重试' });
    } finally {
      this.setData({ saving: false });
    }
  },
});
