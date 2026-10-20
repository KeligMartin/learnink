import { useEffect, useState, type FormEvent } from 'react';
import { api } from '../api/client';
import type { Datasource, Student, StudentCredentials } from '../api/types';
import { useAuth } from '../auth/AuthContext';

function DatasourceSelect({ datasources, value, onChange, label }: {
  datasources: Datasource[];
  value: number;
  onChange: (value: number) => void;
  label: string;
}) {
  return (
    <select aria-label={label} value={value} onChange={(e) => onChange(Number(e.target.value))}>
      {datasources.map((d) => (
        <option key={d.number} value={d.number}>
          {d.number} · {d.name}
        </option>
      ))}
    </select>
  );
}

export function AdminPage() {
  const { user, updateUser } = useAuth();
  const [students, setStudents] = useState<Student[]>([]);
  const [datasources, setDatasources] = useState<Datasource[]>([]);
  const [email, setEmail] = useState('');
  const [newDatasource, setNewDatasource] = useState(1);
  const [credentials, setCredentials] = useState<StudentCredentials | null>(null);
  const [error, setError] = useState<string | null>(null);

  const reload = () => api.students().then(setStudents).catch((e) => setError(e.message));
  useEffect(() => {
    reload();
    api.datasources().then(setDatasources).catch((e) => setError(e.message));
  }, []);

  async function changeDatasource(student: Student, datasource: number) {
    setError(null);
    try {
      const updated = await api.changeStudentDatasource(student.id, datasource);
      setStudents((list) => list.map((s) => (s.id === updated.id ? updated : s)));
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Changement impossible.');
    }
  }

  async function changeOwnDatasource(datasource: number) {
    setError(null);
    try {
      updateUser(await api.changeOwnDatasource(datasource));
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Changement impossible.');
    }
  }

  async function create(event: FormEvent) {
    event.preventDefault();
    setError(null);
    try {
      setCredentials(await api.createStudent(email, newDatasource));
      setEmail('');
      reload();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Création impossible.');
    }
  }

  async function reset(student: Student) {
    if (!window.confirm(`Générer un nouveau mot de passe temporaire pour ${student.email} ?`)) return;
    setError(null);
    try {
      setCredentials(await api.resetStudentPassword(student.id));
      reload();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Réinitialisation impossible.');
    }
  }

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <h1>Étudiants</h1>
          <p className="muted">
            Créez les comptes, choisissez le jeu de données de chaque étudiant (ses exercices et sa base) et suivez sa
            progression.
          </p>
        </div>
        {user && datasources.length > 1 && (
          <label className="inline-label">
            Mon jeu de données (parcours affiché)
            <DatasourceSelect
              datasources={datasources}
              value={user.datasource}
              onChange={changeOwnDatasource}
              label="Mon jeu de données"
            />
          </label>
        )}
      </div>

      <form className="card inline-form" onSubmit={create}>
        <label>
          Email de l'étudiant
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        </label>
        <label className="narrow">
          Jeu de données
          <DatasourceSelect
            datasources={datasources}
            value={newDatasource}
            onChange={setNewDatasource}
            label="Jeu de données du nouvel étudiant"
          />
        </label>
        <button className="button button-primary" type="submit">
          Créer le compte
        </button>
      </form>

      {error && <p className="form-error">{error}</p>}
      {credentials && (
        <div className="card credentials" role="status">
          <p>
            Mot de passe temporaire de <strong>{credentials.student.email}</strong> :{' '}
            <code className="password">{credentials.temporaryPassword}</code>
          </p>
          <p className="muted">
            Transmettez-le à l'étudiant : il ne sera plus affiché. Il devra le changer à sa première connexion.
          </p>
          <button type="button" className="button button-ghost" onClick={() => setCredentials(null)}>
            J'ai noté le mot de passe
          </button>
        </div>
      )}

      <div className="card table-scroll">
        <table className="data-table">
          <thead>
            <tr>
              <th>Email</th>
              <th>Jeu de données</th>
              <th>Niveau actuel</th>
              <th>Progression</th>
              <th>Statut</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {students.map((student) => (
              <tr key={student.id}>
                <td>{student.email}</td>
                <td>
                  <DatasourceSelect
                    datasources={datasources}
                    value={student.datasource}
                    onChange={(value) => changeDatasource(student, value)}
                    label={`Jeu de données de ${student.email}`}
                  />
                </td>
                <td>{student.currentLevel}</td>
                <td>
                  <div className="progress-cell">
                    <div className="bar">
                      <div
                        style={{
                          width: `${student.totalExercises ? (student.completedExercises / student.totalExercises) * 100 : 0}%`,
                        }}
                      />
                    </div>
                    <span>
                      {student.completedExercises}/{student.totalExercises}
                    </span>
                  </div>
                </td>
                <td>{student.mustChangePassword ? <span className="tag">En attente de connexion</span> : 'Actif'}</td>
                <td>
                  <button type="button" className="button button-ghost" onClick={() => reset(student)}>
                    Réinitialiser le mot de passe
                  </button>
                </td>
              </tr>
            ))}
            {students.length === 0 && (
              <tr>
                <td colSpan={6} className="muted">
                  Aucun étudiant pour le moment.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
