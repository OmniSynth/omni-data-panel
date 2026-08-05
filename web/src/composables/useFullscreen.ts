import { onBeforeUnmount, onMounted, ref, type Ref } from 'vue'

type FullscreenElement = HTMLElement & {
  webkitRequestFullscreen?: () => Promise<void> | void
  msRequestFullscreen?: () => Promise<void> | void
}

type FullscreenDocument = Document & {
  webkitFullscreenElement?: Element | null
  webkitExitFullscreen?: () => Promise<void> | void
  msExitFullscreen?: () => Promise<void> | void
}

function fullscreenElement() {
  const doc = document as FullscreenDocument
  return document.fullscreenElement || doc.webkitFullscreenElement || null
}

function notifyChartsResize() {
  requestAnimationFrame(() => {
    window.dispatchEvent(new Event('resize'))
  })
}

/** 对目标元素进入/退出浏览器全屏，并在变化后触发图表 resize。 */
export function useFullscreen(target: Ref<HTMLElement | null | undefined>) {
  const isFullscreen = ref(false)

  function sync() {
    const active = fullscreenElement()
    isFullscreen.value = !!target.value && active === target.value
    notifyChartsResize()
  }

  async function enter() {
    const el = target.value as FullscreenElement | null | undefined
    if (!el) return
    if (el.requestFullscreen) {
      await el.requestFullscreen()
      return
    }
    if (el.webkitRequestFullscreen) {
      await el.webkitRequestFullscreen()
      return
    }
    if (el.msRequestFullscreen) {
      await el.msRequestFullscreen()
    }
  }

  async function exit() {
    if (!fullscreenElement()) return
    const doc = document as FullscreenDocument
    if (document.exitFullscreen) {
      await document.exitFullscreen()
      return
    }
    if (doc.webkitExitFullscreen) {
      await doc.webkitExitFullscreen()
      return
    }
    if (doc.msExitFullscreen) {
      await doc.msExitFullscreen()
    }
  }

  async function toggle() {
    if (isFullscreen.value) await exit()
    else await enter()
  }

  onMounted(() => {
    document.addEventListener('fullscreenchange', sync)
    document.addEventListener('webkitfullscreenchange', sync)
  })

  onBeforeUnmount(() => {
    document.removeEventListener('fullscreenchange', sync)
    document.removeEventListener('webkitfullscreenchange', sync)
    if (target.value && fullscreenElement() === target.value) {
      void exit()
    }
  })

  return { isFullscreen, toggle, enter, exit }
}
