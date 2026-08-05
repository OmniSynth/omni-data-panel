import { toPng } from 'html-to-image'
import { jsPDF } from 'jspdf'

const CAPTURE_DELAY_MS = 300
const PIXEL_RATIO = 2

/** 清洗下载文件名中的非法字符。 */
export function sanitizeExportFilename(name: string, fallback = 'dashboard') {
  const cleaned = (name || fallback).trim().replace(/[\\/:*?"<>|]+/g, '_').replace(/\s+/g, '_')
  return cleaned || fallback
}

function sleep(ms: number) {
  return new Promise<void>((resolve) => {
    window.setTimeout(resolve, ms)
  })
}

function shouldSkipNode(node: HTMLElement) {
  if (node.classList?.contains('toolbar')) return true
  if (node.classList?.contains('no-export')) return true
  if (node.classList?.contains('el-loading-mask')) return true
  if (node.classList?.contains('el-loading-spinner')) return true
  return false
}

async function capturePng(el: HTMLElement) {
  el.classList.add('exporting')
  try {
    await sleep(CAPTURE_DELAY_MS)
    return await toPng(el, {
      pixelRatio: PIXEL_RATIO,
      cacheBust: true,
      filter: (node) => !(node instanceof HTMLElement && shouldSkipNode(node)),
    })
  } finally {
    el.classList.remove('exporting')
  }
}

function triggerDownload(href: string, filename: string) {
  const link = document.createElement('a')
  link.download = filename
  link.href = href
  link.click()
}

/**
 * 将仪表盘 DOM 导出为 PNG 并触发下载。
 *
 * @param el       仪表盘根节点
 * @param filename 不含扩展名的文件名
 */
export async function exportDashboardPng(el: HTMLElement, filename: string) {
  const dataUrl = await capturePng(el)
  triggerDownload(dataUrl, `${sanitizeExportFilename(filename)}.png`)
}

/**
 * 将仪表盘 DOM 截图后写入 PDF（过长时按页切分）并下载。
 *
 * @param el       仪表盘根节点
 * @param filename 不含扩展名的文件名
 */
export async function exportDashboardPdf(el: HTMLElement, filename: string) {
  const dataUrl = await capturePng(el)
  const image = await loadImage(dataUrl)
  const imgWidth = image.naturalWidth || image.width
  const imgHeight = image.naturalHeight || image.height
  if (imgWidth <= 0 || imgHeight <= 0) {
    throw new Error('empty-capture')
  }

  const orientation = imgWidth >= imgHeight ? 'landscape' : 'portrait'
  const pdf = new jsPDF({
    orientation,
    unit: 'pt',
    format: 'a4',
    compress: true,
  })
  const pageWidth = pdf.internal.pageSize.getWidth()
  const pageHeight = pdf.internal.pageSize.getHeight()
  const margin = 24
  const contentWidth = pageWidth - margin * 2
  const scale = contentWidth / imgWidth
  const renderedHeight = imgHeight * scale
  const pageContentHeight = pageHeight - margin * 2

  if (renderedHeight <= pageContentHeight) {
    pdf.addImage(dataUrl, 'PNG', margin, margin, contentWidth, renderedHeight)
  } else {
    const sliceHeightPx = pageContentHeight / scale
    let offsetY = 0
    let pageIndex = 0
    while (offsetY < imgHeight) {
      if (pageIndex > 0) pdf.addPage()
      const slice = Math.min(sliceHeightPx, imgHeight - offsetY)
      const canvas = document.createElement('canvas')
      canvas.width = imgWidth
      canvas.height = Math.max(1, Math.round(slice))
      const ctx = canvas.getContext('2d')
      if (!ctx) throw new Error('canvas-unavailable')
      ctx.fillStyle = '#ffffff'
      ctx.fillRect(0, 0, canvas.width, canvas.height)
      ctx.drawImage(image, 0, offsetY, imgWidth, slice, 0, 0, imgWidth, slice)
      const sliceUrl = canvas.toDataURL('image/png')
      pdf.addImage(sliceUrl, 'PNG', margin, margin, contentWidth, slice * scale)
      offsetY += slice
      pageIndex += 1
    }
  }

  pdf.save(`${sanitizeExportFilename(filename)}.pdf`)
}

function loadImage(src: string) {
  return new Promise<HTMLImageElement>((resolve, reject) => {
    const image = new Image()
    image.onload = () => resolve(image)
    image.onerror = () => reject(new Error('image-load-failed'))
    image.src = src
  })
}
