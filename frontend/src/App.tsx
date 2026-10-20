import { lazy, Suspense } from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './auth/AuthContext';
import { Layout } from './components/Layout';
import { RequireAuth } from './components/RequireAuth';
import { AdminPage } from './pages/AdminPage';
import { ChangePasswordPage } from './pages/ChangePasswordPage';
import { DashboardPage } from './pages/DashboardPage';
import { LoginPage } from './pages/LoginPage';

// Monaco et Chart.js ne sont chargés qu'à l'ouverture d'un exercice
const ExercisePage = lazy(() => import('./pages/ExercisePage').then((m) => ({ default: m.ExercisePage })));

export function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route element={<Layout />}>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/change-password" element={<RequireAuth><ChangePasswordPage /></RequireAuth>} />
            <Route path="/" element={<RequireAuth><DashboardPage /></RequireAuth>} />
            <Route
              path="/exercises/:id"
              element={
                <RequireAuth>
                  <Suspense fallback={<div className="page-loading">Chargement de l'éditeur…</div>}>
                    <ExercisePage />
                  </Suspense>
                </RequireAuth>
              }
            />
            <Route path="/admin" element={<RequireAuth role="TEACHER"><AdminPage /></RequireAuth>} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}
