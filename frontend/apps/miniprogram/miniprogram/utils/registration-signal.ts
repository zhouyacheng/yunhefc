// 报名写操作跨页通知：首页/比赛详情改动报名后，标记「我的报名」需要重载。
let dirty = false;

export function markMyRegistrationsDirty(): void {
  dirty = true;
}

export function consumeMyRegistrationsDirty(): boolean {
  const value = dirty;
  dirty = false;
  return value;
}
