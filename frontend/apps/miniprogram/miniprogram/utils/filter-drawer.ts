// 筛选抽屉开关：赛程、活动与账本共用，配合 templates/filter-drawer.wxml 的 filter-trigger。
interface FilterDrawerHost {
  setData(data: { filtersOpen: boolean }): void;
}

export const filterDrawerHandlers = {
  openFilters(this: FilterDrawerHost): void {
    this.setData({ filtersOpen: true });
  },
  closeFilters(this: FilterDrawerHost): void {
    this.setData({ filtersOpen: false });
  },
};
