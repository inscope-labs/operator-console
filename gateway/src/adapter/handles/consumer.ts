/**
 * ConsumerHandle — typed read/write surface for the consumer repository.
 * Spec §11.4.2. Stage 3.2 adds compare + createPullRequest for publish.
 */

import type { GitHubClient } from '../github-client';
import type { RepoConfig } from '../multi-repo-adapter';
import { getSharedRateCoordinator } from '../rate-coordinator';

export interface ContentsResult {
  sha: string;
  content: string;
  etag?: string;
  type: 'file' | 'dir' | 'unknown';
  path: string;
}

export interface RefResult {
  ref: string;
  sha: string;
}

export interface CompareResult {
  status: string;
  aheadBy: number;
  behindBy: number;
  files: string[];
  /** True when any commit in the compare window has >1 parent. */
  hasMergeCommits: boolean;
  /** True when base is an ancestor of head (GitHub status not 'diverged' only-behind). */
  isAncestryOfBase: boolean;
}

export interface PullRequestResult {
  number: number;
  htmlUrl: string;
  headSha: string;
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

  async getContents(path: string, ref?: string): Promise<ContentsResult> {
    const q = ref ? `?ref=${encodeURIComponent(ref)}` : '';
    const apiPath = `/repos/${this.owner}/${this.name}/contents/${path.replace(/^\//, '')}${q}`;
    const { data, etag, headers } = await this.client.request<{
      type?: string;
      path?: string;
      sha?: string;
      content?: string;
      encoding?: string;
    } | Array<unknown>>('GET', apiPath);
    getSharedRateCoordinator().observeHeaders(headers);
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
    const { data, headers } = await this.client.request<{
      ref: string;
      object: { sha: string };
    }>('GET', `/repos/${this.owner}/${this.name}/git/ref/${ref}`);
    getSharedRateCoordinator().observeHeaders(headers);
    return { ref: data.ref, sha: data.object.sha };
  }

  /**
   * Compare base...head for guard inputs (G5–G8, G6).
   */
  async compare(base: string, head: string): Promise<CompareResult> {
    const { data, headers } = await this.client.request<{
      status: string;
      ahead_by: number;
      behind_by: number;
      files?: Array<{ filename: string }>;
      commits?: Array<{ parents?: Array<unknown> }>;
    }>(
      'GET',
      `/repos/${this.owner}/${this.name}/compare/${encodeURIComponent(base)}...${encodeURIComponent(head)}`
    );
    getSharedRateCoordinator().observeHeaders(headers);
    const commits = data.commits ?? [];
    const hasMergeCommits = commits.some((c) => (c.parents?.length ?? 0) > 1);
    // GitHub: 'ahead' | 'behind' | 'identical' | 'diverged'
    const isAncestryOfBase =
      data.status === 'ahead' ||
      data.status === 'identical' ||
      (data.status === 'diverged' && data.ahead_by > 0);
    return {
      status: data.status,
      aheadBy: data.ahead_by,
      behindBy: data.behind_by,
      files: (data.files ?? []).map((f) => f.filename),
      hasMergeCommits,
      isAncestryOfBase,
    };
  }

  /**
   * Open a pull request (publish side-effect).
   */
  async createPullRequest(opts: {
    title: string;
    head: string;
    base: string;
    body?: string;
  }): Promise<PullRequestResult> {
    const { data, headers } = await this.client.request<{
      number: number;
      html_url: string;
      head: { sha: string };
    }>('POST', `/repos/${this.owner}/${this.name}/pulls`, {
      body: {
        title: opts.title,
        head: opts.head,
        base: opts.base,
        body: opts.body ?? '',
      },
    });
    getSharedRateCoordinator().observeHeaders(headers);
    return {
      number: data.number,
      htmlUrl: data.html_url,
      headSha: data.head?.sha ?? '',
    };
  }

  async getRepoMetadata(): Promise<{ private: boolean; full_name: string }> {
    const { data, headers } = await this.client.request<{
      private: boolean;
      full_name: string;
    }>('GET', `/repos/${this.owner}/${this.name}`);
    getSharedRateCoordinator().observeHeaders(headers);
    return { private: data.private, full_name: data.full_name };
  }
}
