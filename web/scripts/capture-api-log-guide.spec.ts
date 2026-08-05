/**
 * 登录本地前端并截取 sys_api_log 看板相关页面。
 * PLAYWRIGHT_SKIP_WEBSERVER=1 npx playwright test scripts/capture-api-log-guide.spec.ts
 */
import { expect, test } from '@playwright/test'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const username = process.env.E2E_USERNAME || 'admin'
const password = process.env.E2E_PASSWORD || 'admin123'
const __dirname = path.dirname(fileURLToPath(import.meta.url))
const OUT = path.join(__dirname, '..', '..', 'docs', 'assets', 'user-guide')
const resultPath = path.join(OUT, 'setup-result.json')

test.setTimeout(120_000)

test('截取接口请求日志看板图文素材', async ({ page }) => {
  fs.mkdirSync(OUT, { recursive: true })
  const setup = JSON.parse(fs.readFileSync(resultPath, 'utf8')) as {
    dashId: string
    datasetId: string
    viewUrl: string
    editUrl: string
  }

  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/login')
  await page.getByLabel('用户名').fill(username)
  await page.getByLabel('密码').fill(password)
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page.locator('body')).not.toContainText('登录失败', { timeout: 15_000 })
  await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 20_000 })
  await page.screenshot({ path: path.join(OUT, '01-home.png'), fullPage: true })

  await page.goto('/models')
  await expect(page.getByRole('heading', { name: /模型/ })).toBeVisible({ timeout: 15_000 })
  await page.waitForTimeout(800)
  await page.screenshot({ path: path.join(OUT, '02-models.png'), fullPage: true })

  await page.goto(`/models/${setup.datasetId}`)
  await page.waitForTimeout(1200)
  await page.screenshot({ path: path.join(OUT, '03-model-detail.png'), fullPage: true })

  await page.goto('/metrics')
  await expect(page.getByRole('heading', { name: /指标/ })).toBeVisible({ timeout: 15_000 })
  await page.waitForTimeout(800)
  await page.screenshot({ path: path.join(OUT, '04-metrics.png'), fullPage: true })

  await page.goto('/questions')
  await page.waitForTimeout(1000)
  await page.screenshot({ path: path.join(OUT, '05-charts.png'), fullPage: true })

  await page.goto(setup.editUrl)
  await page.waitForTimeout(2000)
  await page.screenshot({ path: path.join(OUT, '06-dashboard-edit.png'), fullPage: true })

  // 滚动到参数区
  const paramCard = page.locator('text=仪表盘参数').first()
  if (await paramCard.count()) {
    await paramCard.scrollIntoViewIfNeeded()
    await page.waitForTimeout(400)
    await page.screenshot({ path: path.join(OUT, '07-dashboard-params.png'), fullPage: false })
  }

  await page.goto(setup.viewUrl)
  await page.waitForTimeout(4000)
  await page.screenshot({ path: path.join(OUT, '08-dashboard-view.png'), fullPage: true })

  // 尝试点开日期参数
  const dateLabel = page.getByText('请求日期').first()
  if (await dateLabel.count()) {
    await dateLabel.click({ force: true }).catch(() => {})
    await page.waitForTimeout(500)
    await page.screenshot({ path: path.join(OUT, '09-dashboard-date-filter.png'), fullPage: true })
  }

  // 应用按钮
  const applyBtn = page.getByRole('button', { name: /应用/ })
  if (await applyBtn.count()) {
    await applyBtn.click().catch(() => {})
    await page.waitForTimeout(3000)
    await page.screenshot({ path: path.join(OUT, '10-dashboard-applied.png'), fullPage: true })
  }
})
