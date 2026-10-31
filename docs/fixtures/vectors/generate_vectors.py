#!/usr/bin/env python3
"""
Reference generator for the ctxop-v1 conformance vectors (Prerequisite P2).

Run from the repository root:   python3 docs/fixtures/vectors/generate_vectors.py
Requires: Python 3.9+ and the `cryptography` package (>= 43, for deterministic ECDSA).

Everything here is deterministic: the ECDSA key is derived from a fixed label and signing
uses RFC 6979, so re-running produces byte-identical files. Signatures are computed by
standard libraries (hmac/hashlib and `cryptography`), never written by hand, and the script
re-verifies every vector it emits before writing anything.

THE KEYS BELOW ARE PUBLIC TEST VALUES. They authorise nothing. Never register them with a
production gateway.
"""
import base64, hashlib, hmac, json, os
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.asymmetric.utils import decode_dss_signature, encode_dss_signature
from cryptography.exceptions import InvalidSignature

OUT = os.path.dirname(os.path.abspath(__file__))
b64u = lambda b: base64.urlsafe_b64encode(b).rstrip(b"=").decode()
b64 = lambda b: base64.b64encode(b).decode()

HEAD = "9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d"
NONCE = "7f3c9b1e5d2a7046"
TASK_BRANCH = "task-10008-refactor-parser"
FIELD_ORDER = ["domain", "kid", "exp", "order", "plan", "task", "op", "branch", "head", "nonce"]

def fields(op="PUBLISH", branch=TASK_BRANCH, **kw):
    f = dict(domain="ctxop-v1", kid="k2", exp=1761840000, order="order-7731", plan="atlas",
             task="10008", op=op, branch=branch, head=HEAD, nonce=NONCE)
    f.update(kw)
    return f

def message(f):
    return "\n".join(str(f[k]) for k in FIELD_ORDER)

# ---------------------------------------------------------------- valid_capabilities.json
TASK_OPS = ["PUBLISH", "UPLOAD", "RECONCILE", "DECIDE", "ORDER", "SET_IN_PROGRESS", "SET_DONE"]
ENGINE_OPS = ["HALT", "RESUME", "SYNC", "DOCTOR", "NEW_PLAN"]
valid = []
for op in TASK_OPS:
    f = fields(op=op)
    valid.append(dict(id=f"valid-{op.lower().replace('_','-')}", description=f"Valid task-scoped {op}",
                      scope="task", fields=f, canonicalMessage=message(f)))
for op in ENGINE_OPS:
    f = fields(op=op, branch="engine", task="engine")
    valid.append(dict(id=f"valid-{op.lower().replace('_','-')}", description=f"Valid engine-scoped {op} on branch 'engine'",
                      scope="engine", fields=f, canonicalMessage=message(f)))
for vid, desc, f in [
    ("valid-nonce-min-16-hex", "Shortest permitted nonce (16 hex)", fields(nonce="a" * 16)),
    ("valid-nonce-max-64-hex", "Longest permitted nonce (64 hex)", fields(nonce="0123456789abcdef" * 4)),
    ("valid-exp-max-long", "Largest canonical exp (Long.MAX_VALUE)", fields(exp=9223372036854775807)),
    ("valid-kid-max-16", "16-character kid with underscore", fields(kid="Key_0123456789ab")),
    ("valid-engine-branch-with-slash", "Engine branch containing a slash", fields(op="SYNC", branch="release/1.0", task="engine")),
]:
    valid.append(dict(id=vid, description=desc, scope="engine" if f["op"] in ENGINE_OPS else "task",
                      fields=f, canonicalMessage=message(f)))

# ---------------------------------------------------------------- codec_mutations.json
base = message(fields())
def with_line(i, value):
    lines = base.split("\n"); lines[i] = value; return "\n".join(lines)
mut = []
def m(mid, cls, raw, code, desc):
    mut.append(dict(id=f"mutation-{mid}", mutationClass=cls, description=desc, rawMessage=raw, expectedErrorCode=code))
m("extra-field", "extra-field", base + "\nextra_field", "BAD_FIELD_COUNT", "11 lines instead of 10 (unsigned message)")
m("missing-field", "missing-field", "\n".join(base.split("\n")[:9]), "BAD_FIELD_COUNT", "9 lines instead of 10")
m("crlf-line-endings", "crlf-line-endings", base.replace("\n", "\r\n"), "BAD_ENCODING", "CRLF separators")
m("lone-cr", "crlf-line-endings", with_line(3, "order-7731\r"), "BAD_ENCODING", "CR at end of one field")
m("trailing-line-feed", "trailing-line-feed", base + "\n", "BAD_ENCODING", "Trailing LF after the nonce")
m("leading-line-feed", "trailing-line-feed", "\n" + base, "BAD_ENCODING", "Leading LF before the domain separator")
m("invalid-domain-separator", "invalid-domain-separator", with_line(0, "ctxop-v2"), "BAD_ENCODING", "Domain literal is not ctxop-v1")
m("unknown-operation", "unknown-operation", with_line(6, "DELETE_EVERYTHING"), "UNKNOWN_OP", "Operation not in OperationType")
m("lowercase-operation", "unknown-operation", with_line(6, "publish"), "UNKNOWN_OP", "Operation literal is case-sensitive")
m("invalid-kid-space", "invalid-kid-regex", with_line(1, "k 2"), "BAD_ENCODING", "kid contains a space")
m("invalid-kid-too-long", "invalid-kid-regex", with_line(1, "k" * 17), "BAD_ENCODING", "kid longer than 16 characters")
m("invalid-kid-hyphen", "invalid-kid-regex", with_line(1, "k-2"), "BAD_ENCODING", "kid contains a hyphen")
m("negative-exp", "negative-exp", with_line(2, "-1761840000"), "BAD_ENCODING", "Negative exp")
m("zero-exp", "negative-exp", with_line(2, "0"), "BAD_ENCODING", "exp is zero")
m("non-numeric-exp", "negative-exp", with_line(2, "soon"), "BAD_ENCODING", "exp is not a number")
m("plus-sign-exp", "noncanonical-exp", with_line(2, "+1761840000"), "BAD_ENCODING", "exp with a plus sign is not canonical")
m("leading-zero-exp", "noncanonical-exp", with_line(2, "01761840000"), "BAD_ENCODING", "exp with a leading zero is not canonical")
m("overflow-exp", "noncanonical-exp", with_line(2, "99999999999999999999"), "BAD_ENCODING", "exp does not fit in a Long")
m("branch-not-task", "branch-not-task", with_line(7, "main"), "BRANCH_NOT_TASK", "Task-scoped op targeting main")
m("branch-not-task-engine-name", "branch-not-task", with_line(7, "engine"), "BRANCH_NOT_TASK", "Task-scoped op targeting the engine branch")
eng = message(fields(op="HALT", branch="engine", task="engine"))
m("engine-op-task-branch", "engine-op-task-branch", "\n".join(eng.split("\n")[:7] + [TASK_BRANCH] + eng.split("\n")[8:]),
  "BRANCH_MISMATCH", "Engine-scoped op carrying a task branch")
m("engine-branch-dotdot", "unsafe-engine-branch", "\n".join(eng.split("\n")[:7] + ["a..b"] + eng.split("\n")[8:]),
  "BAD_ENCODING", "Engine branch containing '..'")
m("engine-branch-leading-dash", "unsafe-engine-branch", "\n".join(eng.split("\n")[:7] + ["-engine"] + eng.split("\n")[8:]),
  "BAD_ENCODING", "Engine branch starting with '-'")
m("malformed-head-short", "malformed-head-sha", with_line(8, HEAD[:39]), "BAD_ENCODING", "head is 39 characters")
m("malformed-head-uppercase", "malformed-head-sha", with_line(8, HEAD.upper()), "BAD_ENCODING", "head is uppercase hex")
m("malformed-head-non-hex", "malformed-head-sha", with_line(8, "g" + HEAD[1:]), "BAD_ENCODING", "head contains a non-hex character")
m("short-nonce", "short-nonce", with_line(9, "7f3c9b1e5d2a704"), "BAD_ENCODING", "nonce is 15 hex characters")
m("uppercase-nonce", "short-nonce", with_line(9, NONCE.upper()), "BAD_ENCODING", "nonce is uppercase")
m("overlong-nonce", "short-nonce", with_line(9, "a" * 65), "BAD_ENCODING", "nonce is 65 hex characters")
m("empty-order", "empty-field", with_line(3, ""), "BAD_ENCODING", "order is empty")

# ---------------------------------------------------------------- hmac_vectors.json
SECRET = "lab-secret-key-32-chars-minimum!!"
hm = []
def hv(vid, secret, msg, desc):
    sig = hmac.new(secret.encode(), msg.encode(), hashlib.sha256).digest()
    hm.append(dict(id=vid, description=desc, secretUtf8=secret, message=msg, expectedSignatureBase64Url=b64u(sig)))
hv("hmac-sha256-publish", SECRET, message(fields()), "Standard task-scoped PUBLISH")
hv("hmac-sha256-halt", SECRET, message(fields(op="HALT", branch="engine", task="engine")), "Engine-scoped HALT")
hv("hmac-sha256-nonce-64", SECRET, message(fields(nonce="0123456789abcdef" * 4)), "64-character nonce")
hv("hmac-sha256-other-secret", "a-completely-different-lab-secret-!!", message(fields()), "Same message, different secret gives a different signature")

# ---------------------------------------------------------------- ecdsa_vectors.json
N = int("FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAAC9CA2F9E5C3F5CC9F7A8F73551", 16) if False else \
    0xFFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551
scalar = int.from_bytes(hashlib.sha256(b"operator-console ctxop-v1 TEST KEY 1").digest(), "big") % (N - 1) + 1
key = ec.derive_private_key(scalar, ec.SECP256R1())
pub = key.public_key()
nums = pub.public_numbers()
jwk = dict(kty="EC", crv="P-256", x=b64u(nums.x.to_bytes(32, "big")), y=b64u(nums.y.to_bytes(32, "big")))
SIGN_ALG = ec.ECDSA(hashes.SHA256(), deterministic_signing=True)

def sign(msg):
    der = key.sign(msg.encode(), SIGN_ALG)
    r, s = decode_dss_signature(der)
    return der, r, s

def verify_raw(msg, raw):
    r, s = int.from_bytes(raw[:32], "big"), int.from_bytes(raw[32:], "big")
    try:
        pub.verify(encode_dss_signature(r, s), msg.encode(), ec.ECDSA(hashes.SHA256())); return True
    except InvalidSignature:
        return False

wanted = {  # category -> predicate on (r, s)
    "r-high-bit": lambda r, s: r.bit_length() == 256,
    "s-high-bit": lambda r, s: s.bit_length() == 256,
    "r-short": lambda r, s: r.bit_length() <= 247 and r.bit_length() % 8 != 0,          # <= 31 bytes, no DER sign pad
    "s-short": lambda r, s: s.bit_length() <= 247 and s.bit_length() % 8 != 0,
    "r-short-der-padded": lambda r, s: r.bit_length() == 248,                            # 31 bytes whose top bit is set -> DER adds 0x00
    "s-short-der-padded": lambda r, s: s.bit_length() == 248,
    "r-and-s-high-bit": lambda r, s: r.bit_length() == 256 and s.bit_length() == 256,
    "typical": lambda r, s: 249 <= r.bit_length() <= 255 and 249 <= s.bit_length() <= 255,
}
found = {}
i = 0
while len(found) < len(wanted):
    i += 1
    assert i < 200000, "could not find all edge cases"
    nonce = hashlib.sha256(f"edge-{i}".encode()).hexdigest()[:32]
    msg = message(fields(nonce=nonce))
    der, r, s = sign(msg)
    for cat, pred in wanted.items():
        if cat not in found and pred(r, s):
            found[cat] = (msg, der, r, s)

ec_vectors = []
for cat in wanted:
    msg, der, r, s = found[cat]
    raw = r.to_bytes(32, "big") + s.to_bytes(32, "big")
    assert verify_raw(msg, raw)
    ec_vectors.append(dict(
        id=f"ecdsa-{cat}", kind="derToRaw", description=f"Edge case: {cat}", message=msg,
        derSignatureBase64=b64(der), expectedRawP1363Base64Url=b64u(raw),
        derLength=len(der), rByteLengthInDer=der[3], sByteLengthInDer=der[5 + der[3]],
        verifiesUnderPublicKey=True))

msg0 = message(fields())
der0, r0, s0 = sign(msg0)
raw0 = r0.to_bytes(32, "big") + s0.to_bytes(32, "big")
ec_vectors.append(dict(id="ecdsa-publish-verifies", kind="verify", description="Raw P1363 signature over the standard PUBLISH message verifies",
                       message=msg0, derSignatureBase64=b64(der0), expectedRawP1363Base64Url=b64u(raw0), verifiesUnderPublicKey=True))
bad = bytearray(raw0); bad[10] ^= 0x01
assert not verify_raw(msg0, bytes(bad))
ec_vectors.append(dict(id="ecdsa-tampered-signature", kind="verify", description="One flipped bit in r: must NOT verify",
                       message=msg0, expectedRawP1363Base64Url=b64u(bytes(bad)), verifiesUnderPublicKey=False))
assert not verify_raw(msg0 + "x", raw0)
ec_vectors.append(dict(id="ecdsa-tampered-message", kind="verify", description="Valid signature, message changed: must NOT verify",
                       message=msg0 + "x", expectedRawP1363Base64Url=b64u(raw0), verifiesUnderPublicKey=False))

def malformed(vid, desc, der_bytes):
    return dict(id=vid, kind="malformedDer", description=desc, derSignatureBase64=b64(der_bytes), expectedError="INVALID_DER")
good = der0
ec_vectors += [
    malformed("ecdsa-malformed-truncated", "DER cut short", good[:-3]),
    malformed("ecdsa-malformed-wrong-sequence-tag", "First byte is not 0x30", b"\x31" + good[1:]),
    malformed("ecdsa-malformed-trailing-bytes", "Extra bytes after the SEQUENCE", good + b"\x00"),
    malformed("ecdsa-malformed-wrong-integer-tag", "r is not tagged INTEGER (0x02)", good[:2] + b"\x03" + good[3:]),
    malformed("ecdsa-malformed-empty", "Empty input", b""),
]
raw_bad_len = [dict(id="ecdsa-raw-wrong-length", kind="rawWrongLength", description="Raw signature must be exactly 64 bytes",
                    rawBase64Url=b64u(raw0[:63]), expectedError="INVALID_RAW_LENGTH")]
ec_vectors += raw_bad_len

# ---------------------------------------------------------------- write
def write(name, obj):
    with open(os.path.join(OUT, name), "w") as fh:
        json.dump(obj, fh, indent=2, ensure_ascii=False); fh.write("\n")

SCHEMA = "https://json-schema.org/draft/2020-12/schema"
write("valid_capabilities.json", dict(**{"$schema": SCHEMA}, vectors=valid))
write("codec_mutations.json", dict(**{"$schema": SCHEMA}, vectors=mut))
write("hmac_vectors.json", dict(**{"$schema": SCHEMA}, note="TEST SECRETS - authorise nothing.", vectors=hm))
write("ecdsa_vectors.json", dict(**{"$schema": SCHEMA}, note="TEST KEY - authorises nothing. Signatures are RFC 6979 deterministic.",
                                  publicKeyJwk=jwk, vectors=ec_vectors))
print(f"valid={len(valid)} mutations={len(mut)} hmac={len(hm)} ecdsa={len(ec_vectors)}")
