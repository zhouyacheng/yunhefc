import { openPrivacy, openTerms } from '../../utils/agreement';

// 登录与绑定手机号共用的协议勾选；勾选状态由页面持有，组件只发 change 事件。

Component({
  properties: {
    checked: { type: Boolean, value: false },
  },

  methods: {
    onToggle() {
      this.triggerEvent('change', { checked: !this.data.checked });
    },

    showTerms() {
      openTerms();
    },

    showPrivacy() {
      openPrivacy();
    },
  },
});
