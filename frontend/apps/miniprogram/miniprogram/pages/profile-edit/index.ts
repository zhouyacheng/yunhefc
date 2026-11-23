import type { PlayerProfileUpdate, PlayerView } from '../../types/api';
import { listTeamPlayers, saveMyPlayer, uploadAvatar } from '../../utils/api-client';
import { errorMessage } from '../../utils/api-errors';
import { avatarInitial, avatarSrc } from '../../utils/avatar';
import {
  buildOptions,
  MAX_SECONDARY,
  POSITION_CODES,
  POSITION_LABELS,
  validateAvatar,
  validateProfile,
  withoutPrimary,
} from '../../utils/profile-edit';
import { getPlayer, getUser, setPlayer } from '../../utils/session';

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
    profileLoading: false,
    profileError: '',
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
      initial: avatarInitial(player.name),
      region: player.region ?? '',
      position: player.position,
      positionIndex: Math.max(0, POSITION_CODES.indexOf(player.position)),
      secondaryOptions: buildOptions(
        player.position,
        withoutPrimary(player.secondaryPositions ?? [], player.position),
      ),
      avatarPreview: avatarSrc(player.avatarUrl) ?? '',
      warning: '',
      error: '',
      profileError: '',
    });

    try {
      await this.fetchFullProfile(player);
    } catch (error) {
      this.setData({
        warning: '资料加载失败，请重新填写地区与副位置',
        profileError: errorMessage(error, '资料加载失败，请重试'),
      });
    } finally {
      this.setData({ loading: false });
    }
  },

  async fetchFullProfile(player: PlayerView): Promise<void> {
    const players = await listTeamPlayers({ all: true });
    const self = players.find((item) => item.id === player.id);
    if (!self) {
      this.setData({ warning: '未能加载完整资料，请重新填写地区与副位置' });
      return;
    }
    const secondary = withoutPrimary(self.secondaryPositions ?? [], player.position).slice(
      0,
      MAX_SECONDARY,
    );
    this.setData({
      region: self.region ?? '',
      secondaryOptions: buildOptions(player.position, secondary),
      warning: '',
    });
  },

  async retryProfile() {
    const player = getPlayer();
    if (!player || this.data.profileLoading) {
      return;
    }
    this.setData({ profileLoading: true, warning: '', profileError: '' });
    try {
      await this.fetchFullProfile(player);
    } catch (error) {
      this.setData({
        warning: '资料加载失败，请重新填写地区与副位置',
        profileError: errorMessage(error, '资料加载失败，请重试'),
      });
    } finally {
      this.setData({ profileLoading: false });
    }
  },

  onNameInput(event: ValueEvent) {
    const name = event.detail.value;
    this.setData({ name, initial: avatarInitial(name) });
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
      const uploaded = await uploadAvatar(filePath);
      // mock OSS 返回 mock://，无法渲染；回落到本地临时文件预览。
      this.setData({
        avatarPreview: avatarSrc(uploaded.signedUrl) ?? filePath,
        avatarObjectKey: uploaded.objectKey,
      });
    } catch (error) {
      this.setData({ error: errorMessage(error, '头像上传失败，请重试') });
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
      const payload: PlayerProfileUpdate = {
        name,
        region,
        position: this.data.position,
        secondaryPositions,
      };
      if (this.data.avatarObjectKey) {
        payload.avatarUrl = this.data.avatarObjectKey;
      }
      const saved = await saveMyPlayer(payload);
      setPlayer(saved);
      wx.showToast({ title: '资料已保存', icon: 'none' });
      void wx.navigateBack();
    } catch (error) {
      this.setData({ error: errorMessage(error, '保存失败，请稍后重试') });
    } finally {
      this.setData({ saving: false });
    }
  },
});
