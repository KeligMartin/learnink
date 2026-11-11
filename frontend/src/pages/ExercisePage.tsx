import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../api/client';
import type {
  ExerciseDetail,
  MongoProfile,
  QueryEngine,
  SchemaDescription,
  SubmissionHistory,
  SubmissionResult,
} from '../api/types';
import { DocumentList } from '../components/DocumentList';
import { ResultChart } from '../components/ResultChart';
import { ResultTable } from '../components/ResultTable';
import { RichText } from '../components/RichText';
import { SchemaExplorer } from '../components/SchemaExplorer';
import { ScoreFeedback } from '../components/ScoreFeedback';
import { QueryEditor } from '../components/QueryEditor';

const DEFAULT_QUERY: Record<QueryEngine, string> = {
  SQL: '-- Écrivez votre requête puis Ctrl + Entrée pour l\'exécuter\nSELECT ',
  MONGODB: '// Écrivez votre requête puis Ctrl + Entrée pour l\'exécuter\ndb.',
};

const TOOLBAR_HINT: Record<QueryEngine, string> = {
  SQL: 'Lecture seule · SELECT uniquement',
  MONGODB: 'Lecture seule · find, countDocuments, aggregate, distinct',
};

type ResultView = 'chart' | 'table' | 'documents';

export function ExercisePage() {
  const exerciseId = Number(useParams().id);
  const [exercise, setExercise] = useState<ExerciseDetail | null>(null);
  const [schema, setSchema] = useState<SchemaDescription>();
  const [history, setHistory] = useState<SubmissionHistory[]>([]);
  const [query, setQuery] = useState('');
  const [submission, setSubmission] = useState<SubmissionResult | null>(null);
  const [running, setRunning] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [view, setView] = useState<ResultView>('chart');
  const [schemaError, setSchemaError] = useState<string | null>(null);
  const [mongo, setMongo] = useState<MongoProfile | null>(null);

  useEffect(() => {
    setExercise(null);
    setSubmission(null);
    setError(null);
    api
      .exercise(exerciseId)
      .then((detail) => {
        setExercise(detail);
        setQuery(detail.lastQuery ?? DEFAULT_QUERY[detail.engine]);
        setView(detail.chart ? 'chart' : detail.engine === 'MONGODB' ? 'documents' : 'table');
        setSchemaError(null);
        api
          .schema(detail.engine)
          .then(setSchema)
          .catch((e) => setSchemaError(e instanceof Error ? e.message : 'Schéma indisponible.'));
        if (detail.engine === 'MONGODB') {
          api.mongoProfile().then(setMongo).catch(() => setMongo(null));
        }
      })
      .catch((e) => setError(e.message));
    api.history(exerciseId).then(setHistory).catch(() => setHistory([]));
  }, [exerciseId]);

  const run = useCallback(async () => {
    if (running || !query.trim()) return;
    setRunning(true);
    setError(null);
    try {
      const result = await api.submit(exerciseId, query);
      setSubmission(result);
      const [detail, submissions] = await Promise.all([api.exercise(exerciseId), api.history(exerciseId)]);
      setExercise(detail);
      setHistory(submissions);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Exécution impossible.');
    } finally {
      setRunning(false);
    }
  }, [exerciseId, query, running]);

  if (error && !exercise) {
    return (
      <div className="page">
        <p className="form-error">{error}</p>
        <Link to="/">← Retour au parcours</Link>
      </div>
    );
  }
  if (!exercise) return <div className="page-loading">Chargement…</div>;

  const isMongo = exercise.engine === 'MONGODB';
  const needsMongoSetup = isMongo && mongo != null && !mongo.configured && !mongo.sharedDatabaseAvailable;
  const views: { id: ResultView; label: string }[] = [
    ...(exercise.chart ? [{ id: 'chart' as const, label: 'Graphique' }] : []),
    ...(isMongo ? [{ id: 'documents' as const, label: 'Documents' }] : []),
    { id: 'table', label: 'Tableau' },
  ];

  return (
    <div className="workspace">
      <aside className="panel statement">
        <Link to="/" className="back">
          ← Parcours
        </Link>
        <span className="level-number">
          Niveau {exercise.level} · {exercise.levelLabel} · Exercice {exercise.position}
        </span>
        <h1>
          {exercise.title} {exercise.completed && <span title="Exercice réussi">✅</span>}
        </h1>
        <div className="statement-text">
          <RichText text={exercise.statement} />
        </div>
        <div className="statement-meta">
          {exercise.orderMatters && <span className="tag">L'ordre des lignes compte</span>}
          {exercise.chart && <span className="tag">📊 Résultat affiché en graphique</span>}
          {exercise.bestScore != null && (
            <span className="tag">Meilleure note : {exercise.bestScore.toLocaleString('fr-FR')}/10</span>
          )}
        </div>

        <h2>{isMongo ? 'Collections de la base' : 'Schéma de la base'}</h2>
        {schemaError ? <p className="muted">{schemaError}</p> : <SchemaExplorer schema={schema} />}

        {history.length > 0 && (
          <>
            <h2>Mes tentatives</h2>
            <ul className="history">
              {history.map((h) => (
                <li key={h.id}>
                  <button type="button" onClick={() => setQuery(h.query)} title="Recharger cette requête">
                    <span className={h.correct ? 'ok' : 'ko'}>{h.score.toLocaleString('fr-FR')}/10</span>
                    <code>{h.query.replace(/\s+/g, ' ').slice(0, 60)}</code>
                    <time className="muted">
                      {new Date(h.createdAt).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' })}
                    </time>
                  </button>
                </li>
              ))}
            </ul>
          </>
        )}
      </aside>

      <section className="panel editor-panel">
        <div className="editor-toolbar">
          <span className="muted">{TOOLBAR_HINT[exercise.engine]}</span>
          <div className="toolbar-actions">
            {exercise.previousExerciseId && (
              <Link className="button button-ghost" to={`/exercises/${exercise.previousExerciseId}`}>
                ← Précédent
              </Link>
            )}
            {exercise.nextExerciseId && (
              <Link className="button button-ghost" to={`/exercises/${exercise.nextExerciseId}`}>
                Suivant →
              </Link>
            )}
            <button type="button" className="button button-primary" onClick={run} disabled={running}>
              {running ? 'Exécution…' : '▶ Exécuter'} <kbd>Ctrl ↵</kbd>
            </button>
          </div>
        </div>
        {needsMongoSetup && (
          <p className="notice">
            Pour exécuter vos requêtes, renseignez d'abord votre base MongoDB dans{' '}
            <Link to="/mongodb">Ma base MongoDB</Link>.
          </p>
        )}
        <QueryEditor engine={exercise.engine} value={query} onChange={setQuery} onRun={run} schema={schema} />

        <div className="output">
          {error && <p className="form-error">{error}</p>}
          {submission ? (
            <>
              <ScoreFeedback submission={submission} />
              {submission.result &&
                (views.length > 1 ? (
                  <section className="result-views">
                    <div className="segmented" role="tablist" aria-label="Affichage du résultat">
                      {views.map((v) => (
                        <button
                          key={v.id}
                          type="button"
                          role="tab"
                          aria-selected={view === v.id}
                          className={view === v.id ? 'active' : ''}
                          onClick={() => setView(v.id)}
                        >
                          {v.label}
                        </button>
                      ))}
                    </div>
                    {view === 'chart' && exercise.chart ? (
                      <ResultChart result={submission.result} spec={exercise.chart} />
                    ) : view === 'documents' && isMongo ? (
                      <DocumentList result={submission.result} />
                    ) : (
                      <ResultTable result={submission.result} />
                    )}
                  </section>
                ) : (
                  <ResultTable result={submission.result} />
                ))}
            </>
          ) : (
            <p className="muted placeholder">Exécutez votre requête pour voir le résultat et votre note.</p>
          )}
        </div>
      </section>
    </div>
  );
}
