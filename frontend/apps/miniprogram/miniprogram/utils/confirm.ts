/** MP14 二次确认：删除与举报共用同一原生弹窗。 */
export function confirmDialog(title: string, content: string): Promise<boolean> {
  return new Promise<boolean>((resolve) => {
    wx.showModal({
      title,
      content,
      success: (result) => resolve(result.confirm),
      fail: () => resolve(false),
    });
  });
}
