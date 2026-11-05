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
| Accepted extensions | `upload.evidence-extensions` (default `pdf,png,jpg,jpeg`; WebP, HEIC and GIF are refused with `STORAGE_UNSUPPORTED_TYPE` — see CLAUDE.md, #348) |
| Size cap | `upload.max-evidence-size-mb` (default 10) |
| Count cap | `upload.max-evidence-per-dispute` (default 5) |

The row in `dispute_evidence` carries the dispute, the uploader, the storage key, the original filename, the
derived content type, the byte size and a SHA-256 checksum of the stored bytes. The API response carries
everything except the storage key. The staff list uses a separate response contract with a derived
`uploaderSide`: the assignment worker is `WORKER`; the job creator and an authorised contact of the job's company
are `EMPLOYER`. Attachment authorization establishes that invariant at write time, so a former company contact's
historical evidence remains `EMPLOYER` after the contact is removed. The user-facing response contract is unchanged.

## Image metadata

Every non-PDF evidence file is re-encoded before it is stored (`ImageMetadataStripper`, the same code the public
image paths use): the EXIF orientation is applied to the pixels and the image is written again, so EXIF/XMP
segments — GPS position, camera make, body/lens serial numbers, unique image id — and PNG text chunks
(`tEXt`, `zTXt`, `iTXt`, `eXIf`) are not kept. Size and SHA-256 describe the stored, re-encoded bytes, and
`metadata_scrubbed_at` is set. JPEG re-encoding is lossy; integrity from upload onwards rests on the server's
`created_at` and checksum. `DateTimeOriginal` is not read or kept (device-controlled, editable, usually without a
time zone, and personal data).

**PDFs are stored as uploaded.** Their Info dictionary (`/Author`, `/Creator`, `/Producer`, dates) and XMP stream
can name the author and the device or software, and phone "scan to PDF" output embeds JPEGs with their own EXIF.
Removing that needs a PDF rewrite (a PDF library, re-encoding embedded images, and it breaks signed PDFs) and is
tracked separately.

### One-off backfill for evidence stored before the scrub (human step)

Image evidence uploaded before this change still carries its metadata. After the release a staff member holding
`user.deletion.manage` (`TRUST_LEAD` or `OWNER`) runs:

```
POST /admin/dispute-evidence/metadata-scrub?limit=25
```

repeatedly until the response shows `remaining: 0`. Each call handles at most `limit` (1–100; anything else is a 400) rows, one
transaction per row, and writes one admin audit row (`dispute.evidence.scrub`) with the ids it touched. Per row:

1. the row is locked and skipped if it is already scrubbed;
2. the stored object is read and compared with the recorded SHA-256 — a mismatch is reported as
   `CHECKSUM_MISMATCH` and neither the row nor the object is changed;
3. the image is re-encoded (`IMAGE_UNREADABLE` if it cannot be decoded);
4. the clean copy is written to a **new** key and read back; if it does not read back as written the transaction
   rolls back, the new object is deleted and the row is marked `REWRITE_FAILED`;
5. the row switches to the new key, keeps the previous size and checksum in `pre_scrub_file_size` /
   `pre_scrub_checksum_sha256`, and records `metadata_scrubbed_at` and `metadata_scrubbed_by_staff_id`;
6. the old key is queued in `account_media_deletion_outbox` in the same transaction and deleted by the existing
   retrying deletion job after commit.

The original is never overwritten and never deleted before the row points at a verified clean copy, so a crash at
any point leaves the evidence readable; a crash after step 4 can only leave an orphaned clean copy. Rows reported
under `failed` (`OBJECT_UNREADABLE`, `CHECKSUM_MISMATCH`, `IMAGE_UNREADABLE`, `REWRITE_FAILED`) are not retried by
later calls, so `remaining` reaches zero; investigate them (a checksum mismatch means the stored object changed
since upload and must be escalated) and, once fixed, call again with `retryFailed=true`. To see what is left:

```sql
select metadata_scrub_error, count(*)
from dispute_evidence
where is_deleted = false and storage_key is not null
  and content_type in ('image/jpeg', 'image/png') and metadata_scrubbed_at is null
group by metadata_scrub_error;
```

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
- Account anonymization removes it, including the pre-scrub checksum and size kept by the backfill. An open dispute already blocks anonymization (ADR-0012), so by the time the
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
