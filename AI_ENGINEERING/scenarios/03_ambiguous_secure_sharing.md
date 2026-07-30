# Scenario 3 (Ambiguous Requirement) — "Users should be able to share URLs securely"

## The requirement, verbatim

> "Users should be able to share URLs securely."

That's it. No mechanism, no threat model, no acceptance criteria.

## Clarifying the ambiguity before writing any code

Three distinct, defensible readings were identified:

1. **Transport/platform security** - "securely" just means HTTPS everywhere, no
   credentials leaked, standard web security hygiene. Already true of the platform
   generally; doesn't require a *new* feature, just wouldn't be worth calling out as its
   own requirement in the spec.
2. **Access-controlled sharing** - the link itself should only be usable by an intended
   recipient, not anyone who guesses/finds the short code. This implies some kind of gate
   on redirect (password, allowlist, expiring signed token, one-time use, ...).
3. **Confidential destination** - the *original* URL should not be discoverable by someone
   who doesn't have the short link (e.g. not exposed in a public "recently created" feed).
   Already true by construction (no such feed exists in this API), so also not really a
   "new feature."

Reading 1 and 3 don't require new implementation - they're already satisfied or are
platform-wide concerns, not link-level ones. Only reading 2 ("access-controlled sharing")
is a genuine, buildable feature, and it's the one that makes sense of the word "share"
specifically (you're choosing who a link works for, which is what "sharing securely" means
in the every-day sense of the phrase - e.g. sharing a sensitive doc link only with people
who have the password). **Reading 2 was adopted.**

## Implementation options considered for reading 2

| Option | Description | Verdict |
|---|---|---|
| **Password-protected link** | Optional password set at creation; redirect requires it before revealing the destination | **Chosen** |
| Signed, expiring share token | A separate, time-limited token (distinct from the short code itself) required in addition to the short code | Rejected: functionally very close to what `expiresAt` + the short code itself already provides: an unguessable random Base62 code is already a bearer secret. Adding a second bearer token doesn't meaningfully raise the bar and adds real complexity (token issuance/rotation endpoints) for the effort available in this exercise. |
| One-time-view link | Redirect works exactly once, then the link is permanently dead | Rejected as the *primary* mechanism: doesn't match "share URLs securely" as well as it matches "share a secret exactly once" - a different, narrower use case. Also awkward for the common case of sharing a link with one person who might reasonably click it more than once (opens it, closes the tab, wants it again). Could be added later as an additional `maxUses` option without conflicting with the chosen design, but wasn't required for this scope. |
| IP/domain allowlisting | Redirect only works from pre-approved IPs/referrers | Rejected: doesn't fit a URL shortener's actual usage pattern (recipients are rarely on a known, stable IP); would mostly just break legitimate use. |

Password protection was chosen because it directly implements "only people I've shared the
password with can use this link," maps cleanly onto existing infrastructure (bcrypt hashing
already used for user passwords via the same `PasswordEncoder` bean), and is a pattern
every user already intuitively understands (same UX as a password-protected file share).

## What was built

- `urls.password_hash` (nullable) - `NULL` means public link (the default; this feature is
  fully opt-in, doesn't change behavior for anyone who doesn't use it).
- `CreateUrlRequest.password` (optional, 4-72 chars) - hashed with the same `BCryptPasswordEncoder`
  used for user accounts, never stored or logged in plaintext.
- Redirect behavior change: `RedirectController` no longer performs the click-tracked 302
  directly for a password-protected link - it redirects (302) to a static frontend prompt
  page (`protected.html?code=...`), which posts the password to
  `POST /api/v1/urls/{shortCode}/verify-password` (public endpoint, no JWT needed - a
  shared link's recipient generally isn't a registered platform user) and only then
  receives the real destination, at which point the browser navigates there itself.
- **Brute-force mitigation**: `/verify-password` gets a materially tighter rate limit (5
  attempts per 60 seconds per IP/API-key, vs. the platform default of 100/60s) - see
  `RateLimitFilter`'s `VERIFY_PASSWORD_PATTERN` special case. Without this, a password-
  protected link would be trivially brute-forceable given short passwords, which would
  defeat the entire point of the feature.
- Click tracking (`UrlClickedEvent`) is only published once a password-protected link is
  *actually* redirected through (after successful verification), not on the initial
  redirect-to-prompt-page hop - so analytics reflect real visits, not password attempts.

## Assumptions made explicit

- A password-protected link's password is a **shared secret with the recipient**, not tied
  to their platform account - anyone with the password can use the link, matching "sharing"
  (as opposed to per-recipient access control, which would require recipient accounts and
  is a materially bigger feature not implied by the one-sentence requirement).
- No password-attempt lockout beyond rate limiting (no permanent link lockout after N
  failures) - a deliberate choice so a malicious third party can't lock a legitimate
  recipient out just by guessing wrong a few times. Documented as a tradeoff, not an
  oversight.
- The password itself is never returned by any API response, including to the link's
  owner after creation (`UrlResponse.passwordProtected` is a boolean, not the password) -
  the owner is expected to remember/manage the password they set, same as any password.

## Validation

- `RedirectControllerTest#redirectsToPasswordPromptForProtectedLinkWithoutTrackingClick` -
  confirms the prompt-page redirect and confirms no click event fires on that hop.
- `UrlServiceImplTest#verifyPasswordReturnsDestinationWhenNotProtected` /
  `#verifyPasswordReturnsEmptyOnWrongPassword` / `#verifyPasswordReturnsDestinationOnCorrectPassword` -
  the three-way branch (public link, wrong password, correct password) covered explicitly.
- Manual reasoning check on the rate-limit choice: 5/60s is tight enough that a 4-character
  minimum password (the DTO's own floor) still can't be brute-forced in any practical time
  frame over the network, while being loose enough not to lock out a legitimate recipient
  who mistypes a password once or twice.
