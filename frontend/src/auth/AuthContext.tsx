import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api, setUnauthorizedHandler, tokenStore } from '../api/client';
import type { QueryEngine, User } from '../api/types';

interface AuthState {
  user: User | null;
  loading: boolean;
  engine: QueryEngine;
  setEngine: (engine: QueryEngine) => void;
  login: (email: string, password: string) => Promise<User>;
  logout: () => void;
  updateUser: (user: User) => void;
}

const AuthContext = createContext<AuthState | null>(null);
const ENGINE_KEY = 'learnink.engine';

function readEngine(): QueryEngine {
  try {
    return localStorage.getItem(ENGINE_KEY) === 'FIRESTORE' ? 'FIRESTORE' : 'SQL';
  } catch {
    return 'SQL';
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [engine, setEngineState] = useState<QueryEngine>(readEngine);

  const logout = useCallback(() => {
    tokenStore.set(null);
    setUser(null);
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(logout);
    if (!tokenStore.get()) {
      setLoading(false);
      return;
    }
    api
      .me()
      .then(setUser)
      .catch(logout)
      .finally(() => setLoading(false));
  }, [logout]);

  const login = useCallback(async (email: string, password: string) => {
    const response = await api.login(email, password);
    tokenStore.set(response.token);
    setUser(response.user);
    return response.user;
  }, []);

  const setEngine = useCallback((value: QueryEngine) => {
    setEngineState(value);
    try {
      localStorage.setItem(ENGINE_KEY, value);
    } catch {
      /* préférence non mémorisée */
    }
  }, []);

  const value = useMemo(
    () => ({ user, loading, engine, setEngine, login, logout, updateUser: setUser }),
    [user, loading, engine, setEngine, login, logout],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth doit être utilisé dans un AuthProvider');
  return context;
}
