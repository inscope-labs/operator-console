import { describe, it, expect } from 'vitest';
import { parseRepos, assertConsumerPrivacy } from '../../../src/config/repos';

const sample = JSON.stringify({
  consumer: {
    role: 'consumer',
    owner: 'acme',
    name: 'consumer-repo',
    private_required: true,
    permission: 'contents:write',
  },
  product: {
    role: 'product',
    owner: 'acme',
    name: 'product-repo',
    private_required: false,
    permission: 'contents:write',
  },
});

describe('parseRepos', () => {
  it('parses consumer and product', () => {
    const parsed = parseRepos(sample);
    expect(parsed.consumerKeys).toEqual(['consumer']);
    expect(parsed.productKey).toBe('product');
    expect(parsed.map.get('consumer')?.name).toBe('consumer-repo');
  });

  it('rejects missing product', () => {
    const bad = JSON.stringify({
      consumer: {
        role: 'consumer',
        owner: 'a',
        name: 'b',
        private_required: true,
        permission: 'contents:write',
      },
    });
    expect(() => parseRepos(bad)).toThrow(/product/);
  });

  it('rejects invalid JSON', () => {
    expect(() => parseRepos('not-json')).toThrow(/JSON/);
  });
});

describe('assertConsumerPrivacy', () => {
  it('passes when check returns true', async () => {
    const parsed = parseRepos(sample);
    await expect(
      assertConsumerPrivacy(parsed, async () => true)
    ).resolves.toBeUndefined();
  });

  it('fails closed when consumer is not private', async () => {
    const parsed = parseRepos(sample);
    await expect(
      assertConsumerPrivacy(parsed, async () => false)
    ).rejects.toThrow(/G1/);
  });
});
