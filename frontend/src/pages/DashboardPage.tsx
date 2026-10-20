import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import type { Engine, Progress } from '../api/types';
import { useAuth } from '../auth/AuthContext';

export function DashboardPage() {
  const { engine, setEngine } = useAuth();
  const [engines, setEngines] = useState<Engine[]>([]);
  const [progress, setProgress] = useState<Progress | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.engines().then(setEngines).catch(() => setEngines([]));
  }, []);

  useEffect(() => {
    setProgress(null);
    setError(null);
    api
      .progress(engine)
      .then(setProgress)
      .catch((e) => setError(e.message));
  }, [engine]);

  const selected = engines.find((e) => e.type === engine);
  const percent = progress && progress.totalExercises > 0
    ? Math.round((progress.completedExercises / progress.totalExercises) * 100)
    : 0;

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <h1>Mon parcours</h1>
          <p className="muted">Terminez tous les exercices d'un niveau pour débloquer le suivant.</p>
        </div>
        <div className="segmented" role="tablist" aria-label="Moteur de base de données">
          {engines.map((e) => (
            <button
              key={e.type}
              type="button"
              role="tab"
              aria-selected={engine === e.type}
              className={engine === e.type ? 'active' : ''}
              onClick={() => setEngine(e.type)}
            >
              {e.label}
              {!e.available && <span className="soon">bientôt</span>}
            </button>
          ))}
        </div>
      </div>

      {selected && !selected.available ? (
        <div className="card empty">
          <h2>{selected.label}</h2>
          <p className="muted">Ce moteur n'est pas encore disponible. Revenez bientôt !</p>
        </div>
      ) : error ? (
        <p className="form-error">{error}</p>
      ) : !progress ? (
        <div className="page-loading">Chargement…</div>
      ) : (
        <>
          <div className="card overall">
            <div>
              <strong>
                {progress.completedExercises} / {progress.totalExercises}
              </strong>{' '}
              exercices réussis
            </div>
            <div className="bar bar-large">
              <div style={{ width: `${percent}%` }} />
            </div>
            <span className="muted">{percent} %</span>
          </div>

          <div className="levels">
            {progress.levels.map((level) => (
              <section key={level.level} className={`card level ${level.unlocked ? '' : 'locked'}`}>
                <header>
                  <span className="level-number">Niveau {level.level}</span>
                  <h2>{level.label}</h2>
                  <span className="level-status">
                    {!level.unlocked ? '🔒 Verrouillé' : level.completed ? '✅ Terminé' : `${level.exercises.filter((e) => e.completed).length} / ${level.exercises.length}`}
                  </span>
                </header>
                <ol className="exercise-list">
                  {level.exercises.map((exercise) => {
                    const content = (
                      <>
                        <span className={`status-dot ${exercise.completed ? 'done' : exercise.attempts > 0 ? 'tried' : ''}`} />
                        <span className="exercise-title">{exercise.title}</span>
                        {exercise.bestScore != null && (
                          <span className="best-score">{exercise.bestScore.toLocaleString('fr-FR')}/10</span>
                        )}
                      </>
                    );
                    return (
                      <li key={exercise.id}>
                        {level.unlocked ? (
                          <Link to={`/exercises/${exercise.id}`}>{content}</Link>
                        ) : (
                          <span className="disabled">{content}</span>
                        )}
                      </li>
                    );
                  })}
                </ol>
              </section>
            ))}
          </div>
        </>
      )}
    </div>
  );
}
