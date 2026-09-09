# Dispute evidence files

Evidence attached to a dispute is user-uploaded personal media. It is stored as a private object, is never
addressable by a permanent URL, and is reachable only through an authorization check on the dispute it belongs to.

## Storage layout

| Item | Value |
| --- | --- |
| Object prefix | `files/dispute-evidence/` |
| Key shape | `files/dispute-evidence/<uuid>.<extension>` — the uploaded filename never becomes part of the key |
| Object ACL | private; `storage.public-url` is never used for this prefix |
| Signed GET lifetime | `storage.signed-url-minutes` (default 3 minutes) |
| Content-Disposition | `attachment`, stamped on the object at upload time (S3) and on the streamed response (local) |
| Accepted extensions | `upload.evidence-extensions` (default `pdf,png,jpg,jpeg,webp`) |
| Size cap | `upload.max-evidence-size-mb` (default 10) |
| Count cap | `upload.max-evidence-per-dispute` (default 5) |

The row in `dispute_evidence` carries the dispute, the uploader, the storage key, the original filename, the
derived content type, the byte size and a SHA-256 checksum of the stored bytes. The API response carries
everything except the storage key. The staff list uses a separate response contract with a derived
`uploaderSide`: the assignment worker is `WORKER`; the job creator and an authorised contact of the job's company
are `EMPLOYER`. Attachment authorization establishes that invariant at write time, so a former company contact's
historical evidence remains `EMPLOYER` after the contact is removed. The user-facing response contract is unchanged.

## Access

| Who | What |
| --- | --- |
| A party of the assignment (worker, job creator, or a company contact of the job's company) | attach, list and download while the dispute is not `RESOLVED`; list and download stay open after resolution |
| Anyone else, including a logged-in stranger holding the UUID | the same 404 and the same `errorCode` as a dispute that does not exist |
| Staff holding `dispute.resolve` | list metadata and download; both calls land in the admin audit log |

`/uploads/files/**` cannot serve these objects: that route resolves ownership through `media`, and evidence has no
`media` row, so it answers 404. Evidence is reachable only through the dispute-scoped endpoints.

## What this does not do

There is no malware, macro or embedded-script scanning anywhere in this repository and none was added. What is
enforced is extension whitelisting, a size cap, a per-dispute count cap, a magic-byte check that the bytes match
the claimed extension, a key that cannot carry the uploaded filename, and a download that is always an attachment
with `X-Content-Type-Options: nosniff`. A malicious but well-formed PDF or image is stored and served. Real
scanning needs an external service (an S3 upload trigger into a scanner, or an ICAP gateway) and its own quarantine
state on the row; it belongs in its own issue.

## Retention and erasure

- Evidence follows the dispute. There is no independent retention clock today; the row lives as long as the
  dispute row does.
- Account anonymization removes it. An open dispute already blocks anonymization (ADR-0012), so by the time the
  scheduler runs the dispute is closed. The anonymizer clears the key, checksum, content type and size, redacts the
  filename, soft-deletes the row, and enqueues the storage key into `account_media_deletion_outbox` — the same
  outbox and the same retry loop as profile pictures, CVs and identity documents. See
  `docs/runbooks/account-anonymization.md`.
- Both the uploader's own evidence and evidence on any dispute of an assignment the user was a party to are
  removed, matching the existing treatment of `disputes.reason`.
- A row whose upload failed after the object was written is never left behind: the object is deleted when the
  transaction rolls back, so a rolled-back attach leaves neither a row nor an object.
- Bucket lifecycle rules can be attached to the `files/dispute-evidence/` prefix independently of the rest of
  `files/`; that is why the prefix is nested rather than flat.
