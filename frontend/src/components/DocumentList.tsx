import type { QueryResult } from '../api/types';

function pretty(document: string) {
  try {
    return JSON.stringify(JSON.parse(document), null, 2);
  } catch {
    return document;
  }
}

/** Documents renvoyés par MongoDB, affichés tels quels (JSON indenté). */
export function DocumentList({ result }: { result: QueryResult }) {
  const documents = result.documents ?? [];
  return (
    <div className="result">
      <div className="result-meta">
        <span>
          {result.tooManyRows ? `Plus de ${result.totalRows}` : result.totalRows} document{result.totalRows > 1 ? 's' : ''}
          {documents.length < result.totalRows && ` · ${documents.length} affichés`}
        </span>
        <span>{result.executionTimeMs.toLocaleString('fr-FR')} ms</span>
      </div>
      <ol className="documents">
        {documents.map((document, i) => (
          <li key={i}>
            <pre>{pretty(document)}</pre>
          </li>
        ))}
      </ol>
    </div>
  );
}
