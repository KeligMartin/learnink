import type { QueryResult } from '../api/types';

export function ResultTable({ result }: { result: QueryResult }) {
  const displayed = result.rows.length;
  return (
    <div className="result">
      <div className="result-meta">
        <span>
          {result.tooManyRows ? `Plus de ${result.totalRows}` : result.totalRows} ligne{result.totalRows > 1 ? 's' : ''}
          {displayed < result.totalRows && ` · ${displayed} affichées`}
        </span>
        <span>{result.executionTimeMs.toLocaleString('fr-FR')} ms</span>
      </div>
      {result.columns.length > 0 && (
        <div className="table-scroll">
          <table className="data-table">
            <thead>
              <tr>
                {result.columns.map((column, i) => (
                  <th key={i}>{column}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {result.rows.map((row, i) => (
                <tr key={i}>
                  {row.map((cell, j) => (
                    <td key={j} className={cell === null ? 'null' : typeof cell === 'number' ? 'num' : undefined}>
                      {cell === null ? 'NULL' : String(cell)}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
