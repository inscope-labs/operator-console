/**
 * ProductHandle — product repository surface.
 * Spec §11.4.3. Write methods remain TODO until Phase 2.
 */

import type { GitHubClient } from '../github-client';
import type { RepoConfig } from '../multi-repo-adapter';
import type { ContentsResult, RefResult } from './consumer';

export class ProductHandle {
  constructor(
    private readonly client: GitHubClient,
    private readonly repo: RepoConfig
  ) {}

  async getContents(path: string, ref?: string): Promise<ContentsResult> {
    // Reuse the same shape as consumer for reads.
    const q = ref ? `?ref=${encodeURIComponent(ref)}` : '';
    const apiPath = `/repos/${this.repo.owner}/${this.repo.name}/contents/${path.replace(/^\//, '')}${q}`;
    const { data, etag } = await this.client.request<{
      type?: string;
      path?: string;
      sha?: string;
      content?: string;
      encoding?: string;
    } | Array<unknown>>('GET', apiPath);
    if (Array.isArray(data)) {
      return { sha: '', content: '', etag, type: 'dir', path };
    }
    const type =
      data.type === 'file' ? 'file' : data.type === 'dir' ? 'dir' : 'unknown';
    let content = '';
    if (type === 'file' && data.content && data.encoding === 'base64') {
      content = atob(data.content.replace(/\n/g, ''));
    }
    return {
      sha: data.sha ?? '',
      content,
      etag,
      type,
      path: data.path ?? path,
    };
  }

  async getRef(branch: string): Promise<RefResult> {
    const ref = branch.startsWith('refs/') ? branch : `heads/${branch}`;
    const { data } = await this.client.request<{
      ref: string;
      object: { sha: string };
    }>('GET', `/repos/${this.repo.owner}/${this.repo.name}/git/ref/${ref}`);
    return { ref: data.ref, sha: data.object.sha };
  }

  // Phase 2:
  // async createCommitFromSha(...): Promise<string>
  // async createPullRequest(...): Promise<{ number: number }>
}
