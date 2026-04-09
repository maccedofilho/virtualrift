import { describe, expect, it } from 'vitest';
import { API_VERSION } from '../index';

describe('API client package', () => {
  it('exports the current API version constant', () => {
    expect(API_VERSION).toBe('v1');
  });

  it('keeps API version prefixed with v', () => {
    expect(API_VERSION.startsWith('v')).toBe(true);
  });
});
