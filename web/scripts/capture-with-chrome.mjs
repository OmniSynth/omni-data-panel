/**
 * 使用本机 Chrome 截取看板图文（避免下载 Playwright 自带浏览器）。
 * node scripts/capture-with-chrome.mjs
 */
import { chromium } from 'playwright'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const BASE = process.env.PLAYWRIGHT_BASE_URL || 'http://localhost:5173'
const USER = process.env.E2E_USERNAME || 'admin'
const PASS = process.env.E2E_PASSWORD || 'admin123'
const __dirname = path.dirname(fileURLToPath(import.meta.url))
const OUT = path.join(__dirname, '..', '..', 'docs', 'assets', 'user-guide')
const setup = JSON.parse(fs.readFileSync(path.join(OUT, 'setup-result.json'), 'utf8'))

async function shot(page, name, fullPage = true) {
  const file = path.join(OUT, name)
  await page.screenshot({ path: file, fullPage })
  console.log('saved', name)
}

async function main() {
  fs.mkdirSync(OUT, { recursive: true })
  const browser = await chromium.launch({
    channel: 'chrome',
    headless: true,
  })
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
  page.setDefaultTimeout(30_000)

  await page.goto(`${BASE}/login`)
  await page.getByLabel('用户名').fill(USER)
  await page.getByLabel('密码').fill(PASS)
  await page.getByRole('button', { name: '登录' }).click()
  await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 30_000 })
  await page.waitForTimeout(1000)
  await shot(page, '01-home.png')

  await page.goto(`${BASE}/models`)
  await page.waitForTimeout(1200)
  await shot(page, '02-models.png')

  await page.goto(`${BASE}/models/${setup.datasetId}`)
  await page.waitForTimeout(1500)
  await shot(page, '03-model-detail.png')

  // 若详情是弹窗而非路由，回到列表点编辑
  if (!(await page.locator('text=外部接口请求日志').count())) {
    await page.goto(`${BASE}/models`)
    await page.waitForTimeout(800)
    const row = page.locator('tr', { hasText: '外部接口请求日志' }).first()
    if (await row.count()) {
      await row.getByRole('button', { name: /编辑/ }).click().catch(async () => {
        await row.click()
      })
      await page.waitForTimeout(1000)
      await shot(page, '03-model-detail.png')
    }
  }

  await page.goto(`${BASE}/metrics`)
  await page.waitForTimeout(1200)
  await shot(page, '04-metrics.png')

  await page.goto(`${BASE}/questions`)
  await page.waitForTimeout(1200)
  await shot(page, '05-charts.png')

  await page.goto(setup.editUrl)
  await page.waitForTimeout(2500)
  await shot(page, '06-dashboard-edit.png')

  const paramHeading = page.getByText('仪表盘参数').first()
  if (await paramHeading.count()) {
    await paramHeading.scrollIntoViewIfNeeded()
    await page.waitForTimeout(500)
    await shot(page, '07-dashboard-params.png', false)
  }

  await page.goto(setup.viewUrl)
  await page.waitForTimeout(5000)
  await shot(page, '08-dashboard-view.png')

  // 填趋势日期并应用
  const start = page.locator('.dashboard-parameter-bar, .param-bar, body').first()
  // 尝试找日期输入
  const dateInputs = page.locator('input[placeholder*="日期"], .el-date-editor input')
  const n = await dateInputs.count()
  console.log('date inputs', n)
  if (n >= 2) {
    await dateInputs.nth(0).click({ force: true })
    await dateInputs.nth(0).fill('2025-01-01').catch(() => {})
    await dateInputs.nth(1).click({ force: true })
    await dateInputs.nth(1).fill('2026-08-05').catch(() => {})
  }
  await shot(page, '09-dashboard-date-filter.png')

  const apply = page.getByRole('button', { name: /应用/ })
  if (await apply.count()) {
    await apply.click()
    await page.waitForTimeout(4000)
  }
  await shot(page, '10-dashboard-applied.png')

  await browser.close()
  console.log('done', OUT)
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
