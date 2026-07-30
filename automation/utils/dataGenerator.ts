import { faker } from '@faker-js/faker';

/**
 * Central random-data source. Every generator is seeded off faker's global RNG (not
 * re-seeded per call), so a full test-run's data is unique but a single `faker.seed(n)`
 * call in global-setup can make a specific run reproducible for debugging.
 */
export const DataGenerator = {
  username(): string {
    return `qa_${faker.internet.username().toLowerCase().replace(/[^a-z0-9]/g, '')}_${Date.now().toString(36)}`;
  },

  email(): string {
    return faker.internet.email({ provider: 'automation.test' }).toLowerCase();
  },

  /** Matches the backend's real password policy: 8+ chars, upper, lower, digit (see RegisterRequest). */
  strongPassword(): string {
    return `Qa${faker.string.alphanumeric(6)}9!`;
  },

  longUrl(pathSegments = 3): string {
    const segments = Array.from({ length: pathSegments }, () => faker.lorem.slug());
    return `https://${faker.internet.domainName()}/${segments.join('/')}?ref=${faker.string.alphanumeric(8)}`;
  },

  apiKeyName(): string {
    return `qa-key-${faker.word.noun()}-${faker.string.alphanumeric(6)}`;
  },

  /** ISO instant N days in the future/past — negative days yields an already-expired timestamp. */
  isoDateOffsetDays(days: number): string {
    const date = new Date();
    date.setUTCDate(date.getUTCDate() + days);
    return date.toISOString();
  },

  uuidLike(): string {
    return faker.string.uuid();
  },

  randomShortCode(length = 7): string {
    return faker.string.alphanumeric(length);
  },
};

/** Deliberately malicious/edge-case payload catalogue for input-validation & security tests. */
export const BoundaryPayloads = {
  empty: '',
  whitespaceOnly: '   ',
  leadingSpaces: '   https://example.com',
  trailingSpaces: 'https://example.com   ',
  maxLengthUrl: `https://example.com/${'a'.repeat(2000)}`,
  unicode: 'https://example.com/café-über-naïve',
  emoji: 'https://example.com/🚀🔥💯',
  sqlInjection: "https://example.com/'; DROP TABLE urls; --",
  xssScriptTag: 'https://example.com/<script>alert(1)</script>',
  xssImgOnError: '<img src=x onerror=alert(1)>',
  htmlInjection: '<b>bold</b><iframe src="javascript:alert(1)">',
  jsonInjection: '{"malicious": true, "__proto__": {"polluted": true}}',
  javascriptScheme: 'javascript:alert(document.cookie)',
  dataScheme: 'data:text/html,<script>alert(1)</script>',
  notAUrl: 'this is not a url at all',
  duplicateSlashes: 'https://example.com//double//slash',
} as const;
