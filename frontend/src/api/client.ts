import type {
  Datasource,
  Engine,
  ExerciseDetail,
  LoginResponse,
  Progress,
  QueryEngine,
  SchemaDescription,
  Student,
  StudentCredentials,
  SubmissionHistory,
  SubmissionResult,
  User,
} from './types';

const TOKEN_KEY = 'learnink.token';

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
  ) {
    super(message);
  }
}

export const tokenStore = {
  get: (): string | null => {
    try {
      return localStorage.getItem(TOKEN_KEY);
    } catch {
      return null;
    }
  },
  set: (token: string | null) => {
    try {
      if (token) localStorage.setItem(TOKEN_KEY, token);
      else localStorage.removeItem(TOKEN_KEY);
    } catch {
      /* stockage indisponible : la session ne survivra pas au rechargement */
    }
  },
};

let onUnauthorized: () => void = () => {};

export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler;
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = tokenStore.get();
  const response = await fetch(`/api${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  });
  if (response.status === 401 && !path.startsWith('/auth/login')) {
    onUnauthorized();
  }
  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new ApiError(response.status, body?.code ?? 'ERROR', body?.message ?? `Erreur ${response.status}`);
  }
  return response.status === 204 ? (undefined as T) : response.json();
}

const post = <T>(path: string, body?: unknown) =>
  request<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) });

export const api = {
  login: (email: string, password: string) => post<LoginResponse>('/auth/login', { email, password }),
  me: () => request<User>('/auth/me'),
  changePassword: (currentPassword: string, newPassword: string) =>
    post<User>('/auth/change-password', { currentPassword, newPassword }),

  engines: () => request<Engine[]>('/engines'),
  schema: (engine: QueryEngine) => request<SchemaDescription>(`/engines/${engine}/schema`),

  progress: (engine: QueryEngine) => request<Progress>(`/progress?engine=${engine}`),
  exercise: (id: number) => request<ExerciseDetail>(`/exercises/${id}`),
  submit: (id: number, query: string) => post<SubmissionResult>(`/exercises/${id}/submissions`, { query }),
  history: (id: number) => request<SubmissionHistory[]>(`/exercises/${id}/submissions`),

  students: () => request<Student[]>('/admin/students'),
  createStudent: (email: string, datasource: number) =>
    post<StudentCredentials>('/admin/students', { email, datasource }),
  datasources: () => request<Datasource[]>('/admin/datasources'),
  changeStudentDatasource: (id: number, datasource: number) =>
    request<Student>(`/admin/students/${id}/datasource`, { method: 'PUT', body: JSON.stringify({ datasource }) }),
  changeOwnDatasource: (datasource: number) =>
    request<User>('/admin/me/datasource', { method: 'PUT', body: JSON.stringify({ datasource }) }),
  resetStudentPassword: (id: number) => post<StudentCredentials>(`/admin/students/${id}/reset-password`),
};
