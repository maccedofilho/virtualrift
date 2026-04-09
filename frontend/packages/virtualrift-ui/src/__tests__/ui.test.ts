import { describe, expect, it } from 'vitest';
import { VR_UI_VERSION } from '../index';

describe('virtualrift ui package', () => {
  it('exports the package version constant', () => {
    expect(VR_UI_VERSION).toBe('0.0.1');
  });
});
