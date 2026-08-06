import { afterEach, describe, expect, it, vi } from 'vitest'
import { applyDocumentTitle } from '@/siteBranding'

describe('applyDocumentTitle', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('有站点名时写入 document.title', () => {
    const state = { title: '旧标题' }
    vi.stubGlobal('document', {
      get title() {
        return state.title
      },
      set title(value: string) {
        state.title = value
      },
    })
    applyDocumentTitle('  测试站点  ')
    expect(state.title).toBe('测试站点')
  })

  it('空值不改写标题', () => {
    const state = { title: '保持原样' }
    vi.stubGlobal('document', {
      get title() {
        return state.title
      },
      set title(value: string) {
        state.title = value
      },
    })
    applyDocumentTitle('')
    applyDocumentTitle(null)
    applyDocumentTitle('   ')
    expect(state.title).toBe('保持原样')
  })
})
