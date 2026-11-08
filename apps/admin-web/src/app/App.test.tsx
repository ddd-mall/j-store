import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';
import { App } from './App';
import { AppProviders } from './AppProviders';

describe('空白后台入口', () => {
  it('通过应用 Provider 装配渲染入口，不显示业务菜单', () => {
    render(
      <AppProviders>
        <MemoryRouter>
          <App />
        </MemoryRouter>
      </AppProviders>,
    );

    expect(
      screen.getByRole('heading', { name: 'J-Store Admin' }),
    ).toBeVisible();
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument();
  });

  it('未知路径提供返回入口，避免空白错误页', () => {
    render(
      <AppProviders>
        <MemoryRouter initialEntries={['/missing']}>
          <App />
        </MemoryRouter>
      </AppProviders>,
    );

    expect(screen.getByRole('heading', { name: '页面不存在' })).toBeVisible();
    expect(screen.getByRole('link', { name: '返回首页' })).toHaveAttribute(
      'href',
      '/',
    );
  });
});
