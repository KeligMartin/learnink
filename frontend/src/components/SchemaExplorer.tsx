import { useState } from 'react';
import type { SchemaDescription } from '../api/types';

export function SchemaExplorer({ schema }: { schema?: SchemaDescription }) {
  const [open, setOpen] = useState<string | null>(null);
  if (!schema) return <p className="muted">Chargement du schéma…</p>;
  return (
    <ul className="schema">
      {schema.tables.map((table) => (
        <li key={table.name}>
          <button
            type="button"
            className="schema-table"
            aria-expanded={open === table.name}
            onClick={() => setOpen(open === table.name ? null : table.name)}
          >
            <span className="chevron">{open === table.name ? '▾' : '▸'}</span>
            {table.name}
            <span className="muted">{table.columns.length}</span>
          </button>
          {open === table.name && (
            <ul className="schema-columns">
              {table.columns.map((column) => (
                <li key={column.name}>
                  <span className={column.primaryKey ? 'pk' : undefined}>
                    {column.primaryKey && <span title="Clé primaire">🔑 </span>}
                    {column.name}
                  </span>
                  <span className="muted">
                    {column.references ? (
                      <span className="fk" title="Clé étrangère">→ {column.references}</span>
                    ) : (
                      column.type.toLowerCase()
                    )}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </li>
      ))}
    </ul>
  );
}
