/** 首屏尚无卡片数据时的骨架占位布局（12 列网格） */
export const DASHBOARD_SKELETON_LAYOUTS = [
  { x: 0, y: 0, w: 6, h: 4 },
  { x: 6, y: 0, w: 6, h: 4 },
  { x: 0, y: 4, w: 4, h: 3 },
  { x: 4, y: 4, w: 4, h: 3 },
  { x: 8, y: 4, w: 4, h: 3 },
] as const

export function skeletonLayoutStyle(layout: { x: number; y: number; w: number; h: number }) {
  return {
    gridColumn: `${layout.x + 1} / span ${layout.w}`,
    gridRow: `${layout.y + 1} / span ${layout.h}`,
  }
}
