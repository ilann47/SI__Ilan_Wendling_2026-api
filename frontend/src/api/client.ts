import axios, { AxiosError } from 'axios';

const TOKEN_KEY = 'kaneko.token';

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string | null): void {
  if (token) localStorage.setItem(TOKEN_KEY, token);
  else localStorage.removeItem(TOKEN_KEY);
}

/** Cliente axios. baseURL vazio => mesma origem (o dev server faz proxy de /api). */
export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
});

api.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers = config.headers ?? {};
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

let unauthorizedHandler: (() => void) | null = null;
export function setUnauthorizedHandler(fn: () => void): void {
  unauthorizedHandler = fn;
}

api.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    if (error.response?.status === 401 && unauthorizedHandler) {
      unauthorizedHandler();
    }
    return Promise.reject(error);
  },
);

/** Formato ProblemDetail (RFC 7807) devolvido pelo backend. */
export interface ApiProblem {
  status?: number;
  title?: string;
  detail?: string;
  erros?: Record<string, string>;
  traceId?: string;
}

/** Normaliza qualquer erro de chamada em uma mensagem amigavel. */
export function describeError(error: unknown): string {
  const ax = error as AxiosError<ApiProblem>;
  const data = ax?.response?.data;
  if (data) {
    if (data.erros && Object.keys(data.erros).length > 0) {
      return Object.values(data.erros).join(' ');
    }
    if (data.detail) return data.detail;
    if (data.title) return data.title;
  }
  if (ax?.response?.status) return `Erro ${ax.response.status}.`;
  return 'Nao foi possivel contatar o servidor. Verifique se o backend esta no ar.';
}
