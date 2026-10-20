import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

export function Layout() {
  const { user, logout } = useAuth();
  return (
    <div className="app">
      <header className="topbar">
        <NavLink to="/" className="brand">
          <span className="brand-mark">{'{ }'}</span> Learnink
        </NavLink>
        {user && !user.mustChangePassword && (
          <nav>
            <NavLink to="/" end>
              Parcours
            </NavLink>
            {user.role === 'TEACHER' && <NavLink to="/admin">Étudiants</NavLink>}
          </nav>
        )}
        {user && (
          <div className="topbar-user">
            <span className="muted">{user.email}</span>
            <button type="button" className="button button-ghost" onClick={logout}>
              Déconnexion
            </button>
          </div>
        )}
      </header>
      <main>
        <Outlet />
      </main>
    </div>
  );
}
