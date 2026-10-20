import { useEffect, useState } from 'react';

const query = '(prefers-color-scheme: dark)';

export function useColorScheme(): 'light' | 'dark' {
  const [dark, setDark] = useState(() => window.matchMedia(query).matches);
  useEffect(() => {
    const media = window.matchMedia(query);
    const listener = (event: MediaQueryListEvent) => setDark(event.matches);
    media.addEventListener('change', listener);
    return () => media.removeEventListener('change', listener);
  }, []);
  return dark ? 'dark' : 'light';
}
