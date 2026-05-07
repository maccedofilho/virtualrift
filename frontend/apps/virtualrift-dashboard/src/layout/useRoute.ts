import { useEffect, useState } from 'react';

export const ROUTES = ['overview', 'targets', 'scans'] as const;
export type Route = (typeof ROUTES)[number];

const isRoute = (value: string): value is Route => (ROUTES as readonly string[]).includes(value);

const parseHash = (hash: string): Route => {
  const cleaned = hash.replace(/^#\/?/, '').split('/')[0];
  return isRoute(cleaned) ? cleaned : 'overview';
};

const readCurrentRoute = (): Route => {
  if (typeof window === 'undefined') {
    return 'overview';
  }

  return parseHash(window.location.hash);
};

export function useRoute(): [Route, (next: Route) => void] {
  const [route, setRoute] = useState<Route>(readCurrentRoute);

  useEffect(() => {
    const onChange = () => setRoute(readCurrentRoute());
    window.addEventListener('hashchange', onChange);
    return () => window.removeEventListener('hashchange', onChange);
  }, []);

  const navigate = (next: Route) => {
    window.location.hash = `#/${next}`;
  };

  return [route, navigate];
}
