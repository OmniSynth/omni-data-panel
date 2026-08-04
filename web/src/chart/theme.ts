import type { EChartsOption } from 'echarts'
import * as echarts from 'echarts'

/** 数分图表浅色主题名 */
export const CHART_THEME_LIGHT = 'omni-light'
/** 数分图表深色主题名 */
export const CHART_THEME_DARK = 'omni-dark'

const LIGHT_COLORS = [
  '#509ee3', '#34b27b', '#f2a93b', '#e45757', '#8b5cf6',
  '#14b8a6', '#f97316', '#64748b', '#06b6d4', '#ec4899',
]

const DARK_COLORS = [
  '#6eb6f0', '#4ade80', '#fbbf24', '#f87171', '#a78bfa',
  '#2dd4bf', '#fb923c', '#94a3b8', '#22d3ee', '#f472b6',
]

function buildTheme(mode: 'light' | 'dark') {
  const dark = mode === 'dark'
  const text = dark ? '#e5e7eb' : '#374151'
  const muted = dark ? '#9ca3af' : '#6b7280'
  const axis = dark ? '#3a4553' : '#e5e7eb'
  const split = dark ? '#2a3441' : '#f0f2f5'
  const tooltipBg = dark ? 'rgba(26, 34, 45, 0.96)' : 'rgba(255, 255, 255, 0.96)'
  const colors = dark ? DARK_COLORS : LIGHT_COLORS

  return {
    color: colors,
    backgroundColor: 'transparent',
    textStyle: {
      fontFamily: '"Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif',
      color: text,
    },
    title: {
      textStyle: { color: text, fontWeight: 600, fontSize: 14 },
      subtextStyle: { color: muted, fontSize: 12 },
    },
    legend: {
      textStyle: { color: muted, fontSize: 12 },
      itemWidth: 12,
      itemHeight: 8,
      icon: 'roundRect',
    },
    tooltip: {
      backgroundColor: tooltipBg,
      borderColor: dark ? '#2a3441' : '#e5e7eb',
      borderWidth: 1,
      textStyle: { color: text, fontSize: 12 },
      extraCssText: 'border-radius:8px;box-shadow:0 8px 24px rgba(15,23,42,0.12);',
      axisPointer: {
        type: 'shadow',
        shadowStyle: { color: dark ? 'rgba(80,158,227,0.12)' : 'rgba(80,158,227,0.08)' },
        lineStyle: { color: '#509ee3', width: 1, type: 'dashed' },
      },
    },
    categoryAxis: {
      axisLine: { lineStyle: { color: axis } },
      axisTick: { show: false },
      axisLabel: { color: muted, fontSize: 11 },
      splitLine: { show: false },
    },
    valueAxis: {
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: muted, fontSize: 11 },
      splitLine: { lineStyle: { color: split, type: 'dashed' } },
    },
    bar: {
      barMaxWidth: 36,
      itemStyle: { borderRadius: [6, 6, 0, 0] },
    },
    line: {
      smooth: true,
      symbol: 'circle',
      symbolSize: 6,
      lineStyle: { width: 2.5 },
      itemStyle: { borderWidth: 2 },
    },
    pie: {
      itemStyle: {
        borderColor: dark ? '#1a222d' : '#ffffff',
        borderWidth: 2,
        borderRadius: 4,
      },
      label: { color: text, fontSize: 12 },
    },
    funnel: {
      itemStyle: {
        borderColor: dark ? '#1a222d' : '#ffffff',
        borderWidth: 1,
      },
    },
    scatter: {
      itemStyle: { opacity: 0.85 },
    },
  }
}

let registered = false

/** 注册浅色/深色图表主题（幂等） */
export function ensureChartThemes() {
  if (registered) return
  echarts.registerTheme(CHART_THEME_LIGHT, buildTheme('light'))
  echarts.registerTheme(CHART_THEME_DARK, buildTheme('dark'))
  registered = true
}

export function chartThemeName(isDark: boolean) {
  return isDark ? CHART_THEME_DARK : CHART_THEME_LIGHT
}

function isHorizontalBar(option: Record<string, unknown>) {
  const yAxis = option.yAxis
  if (!yAxis || typeof yAxis !== 'object' || Array.isArray(yAxis)) return false
  return (yAxis as { type?: string }).type === 'category'
}

/**
 * 叠加通用美化默认项（grid / 动画 / 系列样式）。
 */
export function withChartPolish(option: Record<string, unknown>, isDark: boolean): EChartsOption {
  const gridDefault = {
    left: 12,
    right: 16,
    top: 36,
    bottom: 16,
    containLabel: true,
  }
  const horizontalBar = isHorizontalBar(option)
  const series = Array.isArray(option.series) ? option.series as Record<string, unknown>[] : []
  const polishedSeries = series.map((item) => {
    const type = String(item.type || '')
    if (type === 'bar') {
      const radius = horizontalBar ? [0, 6, 6, 0] : [6, 6, 0, 0]
      return {
        barMaxWidth: 36,
        ...item,
        itemStyle: {
          borderRadius: radius,
          ...(typeof item.itemStyle === 'object' && item.itemStyle ? item.itemStyle as object : {}),
        },
      }
    }
    if (type === 'line') {
      const hasArea = item.areaStyle !== undefined
      return {
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        ...item,
        lineStyle: {
          width: 2.5,
          ...(typeof item.lineStyle === 'object' && item.lineStyle ? item.lineStyle as object : {}),
        },
        areaStyle: hasArea
          ? {
            opacity: 0.14,
            ...(typeof item.areaStyle === 'object' && item.areaStyle ? item.areaStyle as object : {}),
          }
          : undefined,
      }
    }
    if (type === 'pie') {
      return {
        radius: ['42%', '68%'],
        center: ['50%', '52%'],
        ...item,
        itemStyle: {
          borderColor: isDark ? '#1a222d' : '#ffffff',
          borderWidth: 2,
          borderRadius: 4,
          ...(typeof item.itemStyle === 'object' && item.itemStyle ? item.itemStyle as object : {}),
        },
      }
    }
    return item
  })

  return {
    animationDuration: 450,
    animationEasing: 'cubicOut',
    color: isDark ? DARK_COLORS : LIGHT_COLORS,
    ...option,
    grid: option.grid && typeof option.grid === 'object'
      ? { ...gridDefault, ...(option.grid as object) }
      : gridDefault,
    series: polishedSeries,
  } as EChartsOption
}
