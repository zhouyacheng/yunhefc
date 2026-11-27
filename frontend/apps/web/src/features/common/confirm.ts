import { showConfirmDialog } from 'vant';

/** Vant 二次确认：取消/关闭返回 false，确认返回 true。 */
export async function confirmAction(
  title: string,
  message: string,
  confirmText: string,
): Promise<boolean> {
  try {
    await showConfirmDialog({
      title,
      message,
      confirmButtonText: confirmText,
      cancelButtonText: '取消',
    });
    return true;
  } catch {
    return false;
  }
}
