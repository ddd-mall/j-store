import { expect, test } from '@playwright/test';

test('构建产物可加载，路由回退后可返回入口', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));

  await page.goto('/');
  await expect(page).toHaveTitle('J-Store Admin');
  await expect(
    page.getByRole('heading', { name: 'J-Store Admin' }),
  ).toBeVisible();
  await page.goto('/missing');
  await expect(page.getByRole('heading', { name: '页面不存在' })).toBeVisible();
  await page.getByRole('link', { name: '返回首页' }).click();
  await expect(
    page.getByRole('heading', { name: 'J-Store Admin' }),
  ).toBeVisible();
  expect(errors).toEqual([]);
});
