export type Role = 'STUDENT' | 'TEACHER';
export type QueryEngine = 'SQL' | 'FIRESTORE';

export interface User {
  id: number;
  email: string;
  role: Role;
  mustChangePassword: boolean;
  datasource: number;
}

/** Source de données : un jeu de données et ses exercices. */
export interface Datasource {
  number: number;
  name: string;
}

export interface LoginResponse {
  token: string;
  user: User;
}

export interface Engine {
  type: QueryEngine;
  label: string;
  available: boolean;
}

export interface SchemaColumn {
  name: string;
  type: string;
  primaryKey: boolean;
  references?: string;
}

export interface SchemaDescription {
  tables: { name: string; columns: SchemaColumn[] }[];
}

export interface ExerciseSummary {
  id: number;
  level: number;
  position: number;
  title: string;
  completed: boolean;
  bestScore?: number | null;
  attempts: number;
}

export interface Level {
  level: number;
  label: string;
  unlocked: boolean;
  completed: boolean;
  exercises: ExerciseSummary[];
}

export interface Progress {
  engine: QueryEngine;
  currentLevel: number;
  completedExercises: number;
  totalExercises: number;
  levels: Level[];
}

export type ChartType = 'BAR' | 'HORIZONTAL_BAR' | 'LINE' | 'PIE' | 'DOUGHNUT';

export interface ChartSpec {
  type: ChartType;
  labelColumns: number;
}

export interface ExerciseDetail {
  id: number;
  engine: QueryEngine;
  level: number;
  levelLabel: string;
  position: number;
  title: string;
  statement: string;
  orderMatters: boolean;
  chart?: ChartSpec | null;
  completed: boolean;
  bestScore?: number | null;
  lastQuery?: string | null;
  previousExerciseId?: number | null;
  nextExerciseId?: number | null;
}

export interface ScoreCriterion {
  label: string;
  points: number;
  maxPoints: number;
  comment: string;
}

export interface QueryResult {
  columns: string[];
  rows: unknown[][];
  totalRows: number;
  tooManyRows: boolean;
  executionTimeMs: number;
}

export interface SubmissionResult {
  submissionId: number;
  score: number;
  maxScore: number;
  correct: boolean;
  criteria: ScoreCriterion[];
  hints: string[];
  result?: QueryResult;
  error?: string;
  levelCompleted: boolean;
  nextExerciseId?: number;
}

export interface SubmissionHistory {
  id: number;
  query: string;
  score: number;
  correct: boolean;
  createdAt: string;
}

export interface Student {
  id: number;
  email: string;
  mustChangePassword: boolean;
  datasource: number;
  currentLevel: number;
  completedExercises: number;
  totalExercises: number;
}

export interface StudentCredentials {
  student: Student;
  temporaryPassword: string;
}
