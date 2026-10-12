import { useCallback, useSyncExternalStore } from "react";

/**
 * A small preferences store in localStorage, shared by every component that reads it. The server render and the
 * first client render use the defaults; stored values apply right after hydration. Storage may be unavailable:
 * changes then apply for this visit only.
 */
export function createSettingsStore<T extends object>(
  key: string,
  defaults: T,
  sanitize: (value: unknown) => T,
) {
  const listeners = new Set<() => void>();
  let cached: T | undefined;

  function snapshot(): T {
    if (!cached) {
      try {
        cached = sanitize(JSON.parse(localStorage.getItem(key) ?? "{}"));
      } catch {
        cached = defaults;
      }
    }
    return cached;
  }

  function subscribe(listener: () => void) {
    listeners.add(listener);
    return () => listeners.delete(listener);
  }

  return function useSettings(): [T, (change: Partial<T>) => void] {
    const settings = useSyncExternalStore(subscribe, snapshot, () => defaults);
    const update = useCallback((change: Partial<T>) => {
      cached = sanitize({ ...snapshot(), ...change });
      try {
        localStorage.setItem(key, JSON.stringify(cached));
      } catch {
        // Not persisted, but still applied for this visit.
      }
      for (const listener of listeners) listener();
    }, []);
    return [settings, update];
  };
}
