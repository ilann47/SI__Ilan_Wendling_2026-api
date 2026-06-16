import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from 'react';
import { api, getToken, setToken, setUnauthorizedHandler } from '../api/client';

export type Perfil = 'ADMIN' | 'OPERADOR';

export interface AuthUser {
  login: string;
  perfil: Perfil;
}

interface AuthContextValue {
  user: AuthUser | null;
  isAuthenticated: boolean;
  login: (login: string, senha: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue>(null!);

export function useAuth(): AuthContextValue {
  return useContext(AuthContext);
}

interface JwtPayload {
  sub?: string;
  perfil?: string;
  exp?: number;
}

function decodeToken(token: string): JwtPayload | null {
  try {
    const payload = token.split('.')[1];
    const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
    return JSON.parse(json) as JwtPayload;
  } catch {
    return null;
  }
}

function userFromToken(token: string | null): AuthUser | null {
  if (!token) return null;
  const payload = decodeToken(token);
  if (!payload || (payload.exp && payload.exp * 1000 < Date.now())) {
    setToken(null);
    return null;
  }
  return { login: payload.sub ?? '', perfil: (payload.perfil as Perfil) ?? 'OPERADOR' };
}

interface LoginResponse {
  token: string;
  tipo: string;
  login: string;
  perfil: Perfil;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(() => userFromToken(getToken()));

  const logout = useCallback(() => {
    setToken(null);
    setUser(null);
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(logout);
  }, [logout]);

  const login = useCallback(async (loginName: string, senha: string) => {
    const { data } = await api.post<LoginResponse>('/api/auth/login', {
      login: loginName,
      senha,
    });
    setToken(data.token);
    setUser({ login: data.login, perfil: data.perfil });
  }, []);

  return (
    <AuthContext.Provider value={{ user, isAuthenticated: !!user, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}
