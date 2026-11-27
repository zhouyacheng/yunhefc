import { describeError } from '@yunhefc/api-client';
import { CONTENT_REPORT_PHOTO, type ContentReportTargetType } from '@yunhefc/shared';
import { showToast } from 'vant';

import { confirmAction } from '../common/confirm';
import { reportContent } from './api';

/** 举报动态/照片：确认 → 上报 → 统一结果提示（只通知管理员）。 */
export async function confirmAndReport(
  target: ContentReportTargetType,
  id: number,
): Promise<void> {
  const noun = target === CONTENT_REPORT_PHOTO ? '照片' : '动态';
  if (!(await confirmAction(`举报${noun}`, `确定举报该${noun}并通知管理员？`, '举报'))) {
    return;
  }
  try {
    await reportContent(target, id);
    showToast('已通知管理员');
  } catch (caught) {
    showToast(describeError(caught, '举报失败，请稍后重试'));
  }
}
