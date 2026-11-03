/**
 * Minimal DER reader for X.509 attestation chains (no dependencies).
 * Handles high-tag-number (>30) context-specific tags.
 */

export interface DerNode {
  tag: number;
  /** Class: 0 universal, 1 application, 2 context, 3 private */
  tagClass: number;
  constructed: boolean;
  /** Tag number (may be >30). */
  tagNumber: number;
  value: Uint8Array;
  children?: DerNode[];
}

export function decodeDer(data: Uint8Array): DerNode {
  const { node } = readNode(data, 0);
  return node;
}

function readNode(
  data: Uint8Array,
  offset: number
): { node: DerNode; next: number } {
  if (offset >= data.length) {
    throw new Error('DER: truncated');
  }
  let o = offset;
  const first = data[o++];
  const tagClass = (first >> 6) & 0x03;
  const constructed = (first & 0x20) !== 0;
  let tagNumber = first & 0x1f;
  if (tagNumber === 0x1f) {
    // high-tag-number form
    tagNumber = 0;
    for (;;) {
      if (o >= data.length) throw new Error('DER: truncated high tag');
      const b = data[o++];
      tagNumber = (tagNumber << 7) | (b & 0x7f);
      if ((b & 0x80) === 0) break;
    }
  }
  if (o >= data.length) throw new Error('DER: truncated length');
  let len = data[o++];
  if (len & 0x80) {
    const n = len & 0x7f;
    if (n === 0 || o + n > data.length) throw new Error('DER: bad length');
    len = 0;
    for (let i = 0; i < n; i++) len = (len << 8) | data[o++];
  }
  if (o + len > data.length) throw new Error('DER: value truncated');
  const value = data.subarray(o, o + len);
  const next = o + len;
  const node: DerNode = {
    tag: first,
    tagClass,
    constructed,
    tagNumber,
    value,
  };
  if (constructed) {
    node.children = [];
    let c = 0;
    while (c < value.length) {
      const r = readNode(value, c);
      node.children.push(r.node);
      c = r.next;
    }
  }
  return { node, next };
}

/** Walk SEQUENCE nodes depth-first. */
export function walk(node: DerNode, visit: (n: DerNode) => void): void {
  visit(node);
  if (node.children) {
    for (const c of node.children) walk(c, visit);
  }
}

export function findOid(node: DerNode, oid: number[]): DerNode | null {
  let found: DerNode | null = null;
  walk(node, (n) => {
    if (n.tagNumber === 6 && !n.constructed) {
      const decoded = decodeOid(n.value);
      if (oidsEqual(decoded, oid)) found = n;
    }
  });
  return found;
}

function oidsEqual(a: number[], b: number[]): boolean {
  if (a.length !== b.length) return false;
  return a.every((v, i) => v === b[i]);
}

export function decodeOid(value: Uint8Array): number[] {
  if (value.length === 0) return [];
  const out: number[] = [Math.floor(value[0] / 40), value[0] % 40];
  let acc = 0;
  for (let i = 1; i < value.length; i++) {
    const b = value[i];
    acc = (acc << 7) | (b & 0x7f);
    if ((b & 0x80) === 0) {
      out.push(acc);
      acc = 0;
    }
  }
  return out;
}

/** Encode OID to DER content bytes (without tag/length). */
export function encodeOid(oid: number[]): Uint8Array {
  if (oid.length < 2) throw new Error('OID too short');
  const body: number[] = [40 * oid[0] + oid[1]];
  for (let i = 2; i < oid.length; i++) {
    let v = oid[i];
    const stack: number[] = [];
    stack.push(v & 0x7f);
    v >>= 7;
    while (v > 0) {
      stack.push(0x80 | (v & 0x7f));
      v >>= 7;
    }
    for (let j = stack.length - 1; j >= 0; j--) body.push(stack[j]);
  }
  return new Uint8Array(body);
}

export function encodeLength(len: number): Uint8Array {
  if (len < 0x80) return new Uint8Array([len]);
  const bytes: number[] = [];
  let v = len;
  while (v > 0) {
    bytes.unshift(v & 0xff);
    v >>= 8;
  }
  return new Uint8Array([0x80 | bytes.length, ...bytes]);
}

export function encodeTlv(
  tag: number,
  content: Uint8Array,
  constructed = false
): Uint8Array {
  const t = constructed ? tag | 0x20 : tag;
  const len = encodeLength(content.length);
  const out = new Uint8Array(1 + len.length + content.length);
  out[0] = t;
  out.set(len, 1);
  out.set(content, 1 + len.length);
  return out;
}

/** Context-specific tag; supports tagNumber > 30. */
export function encodeContextTag(
  tagNumber: number,
  content: Uint8Array,
  constructed = false
): Uint8Array {
  const parts: number[] = [];
  const cons = constructed ? 0x20 : 0;
  if (tagNumber < 30) {
    parts.push(0x80 | cons | tagNumber);
  } else {
    parts.push(0x80 | cons | 0x1f);
    // high-tag-number base-128
    const stack: number[] = [];
    let v = tagNumber;
    stack.push(v & 0x7f);
    v >>= 7;
    while (v > 0) {
      stack.push(0x80 | (v & 0x7f));
      v >>= 7;
    }
    for (let i = stack.length - 1; i >= 0; i--) parts.push(stack[i]);
  }
  const len = encodeLength(content.length);
  const out = new Uint8Array(parts.length + len.length + content.length);
  out.set(parts, 0);
  out.set(len, parts.length);
  out.set(content, parts.length + len.length);
  return out;
}

export function concatBytes(...parts: Uint8Array[]): Uint8Array {
  const n = parts.reduce((s, p) => s + p.length, 0);
  const out = new Uint8Array(n);
  let o = 0;
  for (const p of parts) {
    out.set(p, o);
    o += p.length;
  }
  return out;
}
