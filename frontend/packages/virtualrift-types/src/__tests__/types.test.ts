import { describe, expect, expectTypeOf, it } from 'vitest';
import type { VirtualRiftConfig } from '../index';

describe('virtualrift types package', () => {
  it('keeps VirtualRiftConfig aligned with the exported shape', () => {
    expectTypeOf<VirtualRiftConfig>().toEqualTypeOf<{ version: string }>();
  });

  it('accepts a runtime object matching VirtualRiftConfig', () => {
    const config: VirtualRiftConfig = { version: '1.0.0' };

    expect(config.version).toBe('1.0.0');
  });
});
