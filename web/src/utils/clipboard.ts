/**
 * 复制文本到剪贴板。
 * 局域网 HTTP 等非安全上下文下 Clipboard API 不可用，回退到 textarea + execCommand。
 */
export async function copyText(text: string): Promise<boolean> {
  if (!text) return false
  try {
    if (navigator.clipboard?.writeText && globalThis.isSecureContext) {
      await navigator.clipboard.writeText(text)
      return true
    }
  } catch {
    // fall through
  }
  try {
    const area = document.createElement('textarea')
    area.value = text
    area.setAttribute('readonly', '')
    area.style.position = 'fixed'
    area.style.left = '-9999px'
    area.style.top = '0'
    document.body.appendChild(area)
    area.select()
    area.setSelectionRange(0, area.value.length)
    const ok = document.execCommand('copy')
    document.body.removeChild(area)
    return ok
  } catch {
    return false
  }
}
