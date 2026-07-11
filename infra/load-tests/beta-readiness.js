import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const authenticatedFailures = new Rate('authenticated_failures');
const authenticatedDuration = new Trend('authenticated_duration', true);

export const options = {
  stages: [
    { duration: __ENV.K6_RAMP_UP || '2m', target: Number(__ENV.K6_VUS || 25) },
    { duration: __ENV.K6_STEADY_STATE || '10m', target: Number(__ENV.K6_VUS || 25) },
    { duration: __ENV.K6_RAMP_DOWN || '2m', target: 0 },
  ],
  thresholds: {
    checks: ['rate>0.99'],
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<750', 'p(99)<1500'],
    authenticated_failures: ['rate<0.01'],
    authenticated_duration: ['p(95)<1000', 'p(99)<2000'],
  },
  noConnectionReuse: false,
  userAgent: 'virtualrift-beta-readiness/1.0',
};

const apiBaseUrl = (__ENV.API_BASE_URL || '').replace(/\/$/, '');

export function setup() {
  if (!apiBaseUrl.startsWith('https://')) {
    fail('API_BASE_URL must be an explicit HTTPS URL.');
  }
  if (!__ENV.E2E_USERNAME || !__ENV.E2E_PASSWORD) {
    fail('E2E_USERNAME and E2E_PASSWORD are required.');
  }

  const login = http.post(
    `${apiBaseUrl}/api/v1/auth/token`,
    JSON.stringify({ email: __ENV.E2E_USERNAME, password: __ENV.E2E_PASSWORD }),
    { headers: { 'Content-Type': 'application/json' }, tags: { operation: 'login' } },
  );
  if (!check(login, { 'setup login succeeds': (response) => response.status === 200 })) {
    fail(`Synthetic user login failed with HTTP ${login.status}.`);
  }

  const session = login.json();
  const headers = { Authorization: `Bearer ${session.accessToken}` };
  const profile = http.get(`${apiBaseUrl}/api/v1/auth/me`, {
    headers,
    tags: { operation: 'profile-setup' },
  });
  if (!check(profile, { 'setup profile succeeds': (response) => response.status === 200 })) {
    fail(`Synthetic user profile failed with HTTP ${profile.status}.`);
  }

  return {
    accessToken: session.accessToken,
    refreshToken: session.refreshToken,
    tenantId: profile.json('tenantId'),
  };
}

export default function (session) {
  const requestOptions = {
    headers: { Authorization: `Bearer ${session.accessToken}` },
  };
  const requests = [
    ['GET', `${apiBaseUrl}/api/v1/auth/me`, null, { ...requestOptions, tags: { operation: 'profile' } }],
    ['GET', `${apiBaseUrl}/api/v1/tenants/${session.tenantId}`, null, { ...requestOptions, tags: { operation: 'tenant' } }],
    ['GET', `${apiBaseUrl}/api/v1/tenants/${session.tenantId}/quota`, null, { ...requestOptions, tags: { operation: 'quota' } }],
    ['GET', `${apiBaseUrl}/api/v1/scans`, null, { ...requestOptions, tags: { operation: 'scans' } }],
    ['GET', `${apiBaseUrl}/api/v1/reports`, null, { ...requestOptions, tags: { operation: 'reports' } }],
  ];

  const responses = http.batch(requests);
  for (const response of responses) {
    const success = check(response, {
      'authenticated read returns 200': (item) => item.status === 200,
    });
    authenticatedFailures.add(!success);
    authenticatedDuration.add(response.timings.duration);
  }

  sleep(1);
}

export function teardown(session) {
  http.post(
    `${apiBaseUrl}/api/v1/auth/logout`,
    JSON.stringify({ refreshToken: session.refreshToken }),
    {
      headers: {
        Authorization: `Bearer ${session.accessToken}`,
        'Content-Type': 'application/json',
      },
      tags: { operation: 'logout' },
    },
  );
}
