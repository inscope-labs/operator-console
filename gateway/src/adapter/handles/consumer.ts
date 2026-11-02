/**
 * ConsumerHandle — typed read/write surface for the consumer repository.
 * Spec §11.4.2. Phase 1 implements getContents + getRef only.
 */

import type { GitHubClient } from '../github-client';
import type { RepoConfig } from '../multi-repo-adapter';

export interface ContentsResult {
  sha: string;
  content: string; // decoded UTF-8 when file; empty for directory listings
  etag?: string;
  type: 'file' | 'dir' | 'unknown';
  path: string;
}

export interface RefResult {
  ref: string;
  sha: string;
}

export class ConsumerHandle {
  constructor(
    private readonly client: GitHubClient,
    private readonly repo: RepoConfig
  ) {}

  private get owner(): string {
    return this.repo.owner;
  }
  private get name(): string {
    return this.repo.name;
  }

  /**
   * GET /repos/{owner}/{repo}/contents/{path}
   * Returns decoded file content when type=file.
   */
  async getContents(path: string, ref?: string): Promise<ContentsResult> {
    const q = ref ? `?ref=${encodeURIComponent(ref)}` : '';
    const apiPath = `/repos/${this.owner}/${this.name}/contents/${path.replace(/^\//, '')}${q}`;
    const { data, etag } = await this.client.request<{
      type?: string;
      path?: string;
      sha?: string;
      content?: string;
      encoding?: string;
    } | Array<unknown>>('GET', apiPath);

    if (Array.isArray(data)) {
      return {
        sha: '',
        content: '',
        etag,
        type: 'dir',
        path,
      };
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

  /**
   * GET /repos/{owner}/{repo}/git/ref/heads/{branch}
   */
  async getRef(branch: string): Promise<RefResult> {
    const ref = branch.startsWith('refs/') ? branch : `heads/${branch}`;
    const apiPath = `/repos/${this.owner}/${this.name}/git/ref/${ref}`;
    const { data } = await this.client.request<{
      ref: string;
      object: { sha: string };
    }>('GET', apiPath);
    return { ref: data.ref, sha: data.object.sha };
  }

  /**
   * GET /repos/{owner}/{repo} — used for G1 privacy check at startup.
   */
  async getRepoMetadata(): Promise<{ private: boolean; full_name: string }> {
    const { data } = await this.client.request<{
      private: boolean;
      full_name: string;
    }>('GET', `/repos/${this.owner}/${this.name}`);
    return { private: data.private, full_name: data.full_name };
  }
}
