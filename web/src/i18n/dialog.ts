import { ElMessageBox, type ElMessageBoxOptions } from 'element-plus'
import { t } from '@/i18n'

type MessageBoxType = NonNullable<ElMessageBoxOptions['type']>

/**
 * 带当前界面语言按钮文案的确认框（MessageBox 挂载到 body，不继承 ElConfigProvider）。
 */
export function confirmBox(
  message: string,
  title: string,
  options?: ElMessageBoxOptions,
) {
  return ElMessageBox.confirm(message, title, {
    confirmButtonText: t('common.confirm'),
    cancelButtonText: t('common.cancel'),
    type: 'warning' as MessageBoxType,
    ...options,
  })
}

/**
 * 带当前界面语言按钮文案的输入框。
 */
export function promptBox(
  message: string,
  title: string,
  options?: ElMessageBoxOptions,
) {
  return ElMessageBox.prompt(message, title, {
    confirmButtonText: t('common.confirm'),
    cancelButtonText: t('common.cancel'),
    ...options,
  })
}
