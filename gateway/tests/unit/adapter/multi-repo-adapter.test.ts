import { describe, it, expect } from 'vitest';
import { MultiRepoGitHubAdapter } from '../../../src/adapter/multi-repo-adapter';
import { parseRepos } from '../../../src/config/repos';

const reposJson = JSON.stringify({
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

describe('MultiRepoGitHubAdapter.create', () => {
  it('constructs with skipPrivacyCheck and exposes handles', async () => {
    // Minimal PEM will fail token fetch only when a call is made; create itself
    // with skipPrivacyCheck should succeed without network.
    const adapter = await MultiRepoGitHubAdapter.create({
      reposJson,
      secrets: {
        appId: '1',
        privateKeyPem: '-----BEGIN PRIVATE KEY-----\nMIIE\n-----END PRIVATE KEY-----',
        installationId: '1',
      },
      skipPrivacyCheck: true,
      fetchImpl: async () => new Response('{}', { status: 200 }),
    });
    expect(adapter.consumer()).toBeTruthy();
    expect(adapter.product()).toBeTruthy();
    expect(() => adapter.readOnly('missing')).toThrow(/UNKNOWN_REPO/);
  });

  it('parseRepos rejects empty map before create', () => {
    expect(() => parseRepos('{}')).toThrow();
  });
});
