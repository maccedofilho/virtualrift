// @vitest-environment jsdom

import '@testing-library/jest-dom/vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import App from './App';

afterEach(() => {
  cleanup();
});

describe('VirtualRift Dashboard App', () => {
  it('renders the dashboard heading', () => {
    render(<App />);

    expect(
      screen.getByRole('heading', { name: 'VirtualRift Dashboard' }),
    ).toBeInTheDocument();
  });

  it('renders the current skeleton message', () => {
    render(<App />);

    expect(
      screen.getByText('Dashboard skeleton is ready for implementation.'),
    ).toBeInTheDocument();
  });
});
