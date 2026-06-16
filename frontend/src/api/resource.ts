import { api } from './client';

/** Pagina no formato Spring Data (Page<T>). */
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
  numberOfElements: number;
  empty: boolean;
}

export interface PageParams {
  page?: number;
  size?: number;
  sort?: string;
  [filter: string]: unknown;
}

/** Remove chaves com valor undefined/null/'' antes de enviar como query string. */
function clean(params: PageParams): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') out[key] = value;
  }
  return out;
}

/** Fabrica de funcoes CRUD para um recurso REST paginado. */
export function createResourceApi<TResponse, TRequest = unknown>(basePath: string) {
  return {
    basePath,
    list: (params: PageParams = {}) =>
      api.get<Page<TResponse>>(basePath, { params: clean(params) }).then((r) => r.data),
    get: (id: number | string) =>
      api.get<TResponse>(`${basePath}/${id}`).then((r) => r.data),
    create: (body: TRequest) =>
      api.post<TResponse>(basePath, body).then((r) => r.data),
    update: (id: number | string, body: TRequest) =>
      api.put<TResponse>(`${basePath}/${id}`, body).then((r) => r.data),
    remove: (id: number | string) =>
      api.delete<void>(`${basePath}/${id}`).then(() => undefined),
    /** Executa uma acao customizada (ex.: POST /{id}/baixa). */
    action: <R = unknown, B = unknown>(
      method: 'post' | 'put' | 'delete',
      suffix: string,
      options: { body?: B; params?: Record<string, unknown> } = {},
    ) =>
      api
        .request<R>({ method, url: `${basePath}${suffix}`, data: options.body, params: options.params })
        .then((r) => r.data),
  };
}

export type ResourceApi<TResponse, TRequest = unknown> = ReturnType<
  typeof createResourceApi<TResponse, TRequest>
>;
