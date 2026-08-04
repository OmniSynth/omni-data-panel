import { expect, test } from '@playwright/test'

const username = process.env.E2E_USERNAME
const password = process.env.E2E_PASSWORD
test.skip(!username || !password, '需要配置 E2E_USERNAME 和 E2E_PASSWORD，并运行后端服务')

test('登录并进入数据分析主链路', async ({ page }) => {
  await page.goto('/login')
  await page.getByLabel('用户名').fill(username!)
  await page.getByLabel('密码').fill(password!)
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page.getByRole('heading', { name: /近况如何/ })).toBeVisible()

  await page.getByRole('link', { name: '数据库' }).click()
  await expect(page.getByRole('heading', { name: '数据库' })).toBeVisible()
  await page.getByRole('link', { name: '模型' }).click()
  await expect(page.getByRole('heading', { name: '模型' })).toBeVisible()
  await page.getByRole('link', { name: '指标' }).click()
  await expect(page.getByRole('heading', { name: '指标' })).toBeVisible()
  await page.getByRole('link', { name: '废纸篓' }).click()
  await expect(page.getByRole('heading', { name: '废纸篓' })).toBeVisible()
})
