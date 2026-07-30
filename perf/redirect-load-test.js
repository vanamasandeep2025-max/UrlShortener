// k6 load test for the redirect hot path (the endpoint the <100ms / 1000 req/s NFRs target).
//
// Usage:
//   1. Create a short URL first (e.g. via the frontend or curl) and note its short code.
//   2. k6 run -e BASE_URL=http://localhost -e SHORT_CODE=abc1234 perf/redirect-load-test.js
//
// This is NOT run automatically as part of CI (it needs a running stack + real data);
// it's a developer/ops tool for validating the NFRs before a release. See
// docs/DEPLOYMENT.md for how to interpret the results.
import http from "k6/http";
import { check, sleep } from "k6";
import { Rate, Trend } from "k6/metrics";

const BASE_URL = __ENV.BASE_URL || "http://localhost";
const SHORT_CODE = __ENV.SHORT_CODE || "demo0001";

const errorRate = new Rate("redirect_errors");
const redirectDuration = new Trend("redirect_duration_ms", true);

export const options = {
  scenarios: {
    steady_load: {
      executor: "ramping-arrival-rate",
      startRate: 50,
      timeUnit: "1s",
      preAllocatedVUs: 200,
      maxVUs: 1000,
      stages: [
        { target: 200, duration: "30s" },
        { target: 1000, duration: "1m" },
        { target: 1000, duration: "2m" },
        { target: 0, duration: "30s" },
      ],
    },
  },
  thresholds: {
    // NFR: p95 redirect latency under 100ms.
    "redirect_duration_ms": ["p(95)<100"],
    "redirect_errors": ["rate<0.01"],
    http_req_failed: ["rate<0.01"],
  },
};

export default function () {
  const res = http.get(`${BASE_URL}/${SHORT_CODE}`, { redirects: 0 });

  const ok = check(res, {
    "status is 302": (r) => r.status === 302,
    "has Location header": (r) => r.headers["Location"] !== undefined,
  });
  errorRate.add(!ok);
  redirectDuration.add(res.timings.duration);

  sleep(0.01);
}
