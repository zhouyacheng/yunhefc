/** 队内赛（1）与训练赛（2）：允许阵容/分组，事件按分组分队。 */
export function isInternalMatchType(type: number | null | undefined): boolean {
  return type === 1 || type === 2;
}
