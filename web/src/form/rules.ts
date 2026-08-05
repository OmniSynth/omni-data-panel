import type { FormInstance, FormItemRule } from 'element-plus'

/** 必填规则 */
export function requiredRule(message: string, trigger: FormItemRule['trigger'] = 'blur'): FormItemRule {
  return { required: true, message, trigger }
}

/** 邮箱格式规则（不强制必填，需与 requiredRule 组合） */
export function emailRule(message: string): FormItemRule {
  return { type: 'email', message, trigger: ['blur', 'change'] }
}

/** 最小长度规则 */
export function minLengthRule(min: number, message: string): FormItemRule {
  return { min, message, trigger: 'blur' }
}

/** 校验表单；失败时 Element Plus 会标红并定位到字段 */
export async function validateForm(form?: FormInstance | null): Promise<boolean> {
  if (!form) return false
  try {
    await form.validate()
    return true
  } catch {
    return false
  }
}
