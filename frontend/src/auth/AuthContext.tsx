import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import * as authApi from '../api/auth';
import type { StudentProfile } from '../api/types';

export type AuthStatus = 'loading' | 'authenticated' | 'anonymous';

export interface AuthContextValue {
  status: AuthStatus;
  user: StudentProfile | null;
  login(studentId: string, password: string): Promise<void>;
  logout(): Promise<void>;
  markAnonymous(): void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>('loading');
  const [user, setUser] = useState<StudentProfile | null>(null);

  useEffect(() => {
    let active = true;
    (async () => {
      try {
        const profile = await authApi.fetchMe();
        if (!active) return;
        if (profile) {
          setUser(profile);
          setStatus('authenticated');
        } else {
          setUser(null);
          setStatus('anonymous');
        }
      } catch {
        if (!active) return;
        setUser(null);
        setStatus('anonymous');
      }
    })();
    return () => {
      active = false;
    };
  }, []);

  const login = useCallback(async (studentId: string, password: string) => {
    const profile = await authApi.login(studentId, password);
    setUser(profile);
    setStatus('authenticated');
  }, []);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } catch {
      // logging out locally is enough; the server session expires on its own
    } finally {
      setUser(null);
      setStatus('anonymous');
    }
  }, []);

  const markAnonymous = useCallback(() => {
    setUser(null);
    setStatus('anonymous');
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({ status, user, login, logout, markAnonymous }),
    [status, user, login, logout, markAnonymous],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used inside <AuthProvider>');
  }
  return ctx;
}
