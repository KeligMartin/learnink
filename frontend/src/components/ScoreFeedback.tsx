import { Link } from 'react-router-dom';
import type { SubmissionResult } from '../api/types';

function scoreTone(score: number): string {
  if (score >= 8) return 'good';
  if (score >= 5) return 'medium';
  return 'bad';
}

const formatPoints = (value: number) => value.toLocaleString('fr-FR', { maximumFractionDigits: 1 });

export function ScoreFeedback({ submission }: { submission: SubmissionResult }) {
  const tone = scoreTone(submission.score);
  return (
    <section className={`feedback feedback-${tone}`} aria-live="polite">
      <div className="feedback-head">
        <div className={`score-badge score-${tone}`}>
          <strong>{formatPoints(submission.score)}</strong>
          <span>/{submission.maxScore}</span>
        </div>
        <div>
          <h3>{submission.correct ? 'Résultat correct' : submission.error ? 'Requête refusée' : 'Résultat incorrect'}</h3>
          {submission.error && <p className="feedback-error">{submission.error}</p>}
          {submission.levelCompleted && <p className="level-up">🎉 Niveau terminé ! Le niveau suivant est débloqué.</p>}
        </div>
        {submission.correct && submission.nextExerciseId && (
          <Link className="button button-primary next" to={`/exercises/${submission.nextExerciseId}`}>
            Exercice suivant →
          </Link>
        )}
      </div>

      <ul className="criteria">
        {submission.criteria.map((criterion) => (
          <li key={criterion.label}>
            <div className="criterion-head">
              <span>{criterion.label}</span>
              <span>
                {formatPoints(criterion.points)} / {formatPoints(criterion.maxPoints)}
              </span>
            </div>
            <div className="bar">
              <div style={{ width: `${(criterion.points / criterion.maxPoints) * 100}%` }} />
            </div>
            <small>{criterion.comment}</small>
          </li>
        ))}
      </ul>

      {submission.hints.length > 0 && (
        <ul className="hints">
          {submission.hints.map((hint) => (
            <li key={hint}>💡 {hint}</li>
          ))}
        </ul>
      )}
    </section>
  );
}
