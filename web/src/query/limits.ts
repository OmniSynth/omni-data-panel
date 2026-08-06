/**
 * 单次查询结果行数软上限（与后端默认 omni.query.max-rows 对齐）。
 * 结果表按此上限提示；表格本身对已返回行做客户端分页，不再二次截断展示。
 */
export const QUERY_RESULT_DISPLAY_LIMIT = 50_000

/** 结果表可选分页大小。 */
export const QUERY_RESULT_PAGE_SIZES = [20, 50, 100, 200, 500] as const
