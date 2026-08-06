/** 将站点名称同步到浏览器标签标题。 */
export function applyDocumentTitle(siteName: string | null | undefined) {
  const name = String(siteName || '').trim()
  if (name) document.title = name
}
