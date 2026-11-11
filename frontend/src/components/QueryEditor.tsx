import Editor, { type OnMount } from '@monaco-editor/react';
import './monacoSetup';
import type { editor, Position } from 'monaco-editor/editor/editor.api';
import { useEffect, useRef } from 'react';
import type { QueryEngine, SchemaDescription } from '../api/types';
import { useColorScheme } from './useColorScheme';

const SQL_KEYWORDS = [
  'SELECT', 'FROM', 'WHERE', 'AND', 'OR', 'NOT', 'IN', 'IS NULL', 'IS NOT NULL', 'LIKE', 'BETWEEN',
  'JOIN', 'INNER JOIN', 'LEFT JOIN', 'RIGHT JOIN', 'ON', 'GROUP BY', 'HAVING', 'ORDER BY', 'ASC', 'DESC',
  'LIMIT', 'DISTINCT', 'AS', 'COUNT', 'SUM', 'AVG', 'MIN', 'MAX', 'ROUND', 'YEAR', 'MONTH', 'EXISTS',
  'UNION', 'WITH', 'CASE', 'WHEN', 'THEN', 'ELSE', 'END', 'OVER', 'PARTITION BY', 'RANK',
];

/** Méthodes mongosh acceptées par la plateforme, puis opérateurs et étapes d'agrégation courants. */
const MONGO_METHODS = ['find', 'findOne', 'countDocuments', 'aggregate', 'distinct', 'sort', 'limit', 'skip', 'count'];
const MONGO_OPERATORS = [
  '$eq', '$ne', '$gt', '$gte', '$lt', '$lte', '$in', '$nin', '$and', '$or', '$not', '$exists', '$regex', '$size',
  '$elemMatch', '$expr', '$match', '$project', '$group', '$sort', '$limit', '$skip', '$unwind', '$lookup', '$count',
  '$addFields', '$sum', '$avg', '$min', '$max', '$first', '$last', '$push', '$round', '$year', '$month',
  '$dayOfMonth', '$concat', '$toUpper', '$cond', '$ifNull', '$arrayElemAt',
];

const LANGUAGE: Record<QueryEngine, string> = { SQL: 'sql', MONGODB: 'javascript' };

interface Props {
  engine: QueryEngine;
  value: string;
  onChange: (value: string) => void;
  onRun: () => void;
  schema?: SchemaDescription;
}

export function QueryEditor({ engine, value, onChange, onRun, schema }: Props) {
  const colorScheme = useColorScheme();
  const runRef = useRef(onRun);
  const schemaRef = useRef(schema);
  const disposables = useRef<{ dispose: () => void }[]>([]);
  runRef.current = onRun;
  schemaRef.current = schema;

  useEffect(() => () => disposables.current.forEach((d) => d.dispose()), []);

  const handleMount: OnMount = (editor, monaco) => {
    // Ctrl + Entrée partout, et ⌘ + Entrée sur macOS
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, () => runRef.current());
    editor.addCommand(monaco.KeyMod.WinCtrl | monaco.KeyCode.Enter, () => runRef.current());
    editor.focus();

    // Autocomplétion : mots-clés du langage, tables / collections et colonnes / champs de la base
    disposables.current.push(
      monaco.languages.registerCompletionItemProvider(LANGUAGE[engine], {
        triggerCharacters: engine === 'MONGODB' ? ['$', '.'] : undefined,
        provideCompletionItems: (model: editor.ITextModel, position: Position) => {
          const word = model.getWordUntilPosition(position);
          const before = model.getValueInRange({
            startLineNumber: position.lineNumber,
            endLineNumber: position.lineNumber,
            startColumn: Math.max(1, word.startColumn - 1),
            endColumn: word.startColumn,
          });
          // En MongoDB, le « $ » qui précède le mot fait partie de l'opérateur à compléter
          const startColumn = engine === 'MONGODB' && before === '$' ? word.startColumn - 1 : word.startColumn;
          const range = {
            startLineNumber: position.lineNumber,
            endLineNumber: position.lineNumber,
            startColumn,
            endColumn: word.endColumn,
          };
          const { Keyword, Class, Field, Method } = monaco.languages.CompletionItemKind;
          const tables = schemaRef.current?.tables ?? [];
          const keywords =
            engine === 'MONGODB'
              ? [
                  ...MONGO_METHODS.map((m) => ({ label: m, kind: Method, insertText: m, range })),
                  ...MONGO_OPERATORS.map((o) => ({ label: o, kind: Keyword, insertText: o, range })),
                ]
              : SQL_KEYWORDS.map((k) => ({ label: k, kind: Keyword, insertText: k, range }));
          const containerLabel = engine === 'MONGODB' ? 'collection' : 'table';
          return {
            suggestions: [
              ...keywords,
              ...tables.map((t) => ({ label: t.name, kind: Class, insertText: t.name, detail: containerLabel, range })),
              ...tables.flatMap((t) =>
                t.columns.map((c) => ({
                  label: c.name,
                  kind: Field,
                  insertText: c.name,
                  detail: `${t.name} · ${c.type.toLowerCase()}`,
                  range,
                })),
              ),
            ],
          };
        },
      }),
    );
  };

  return (
    <div className="editor-frame">
      <Editor
        height="100%"
        language={LANGUAGE[engine]}
        value={value}
        onChange={(v) => onChange(v ?? '')}
        onMount={handleMount}
        theme={colorScheme === 'dark' ? 'vs-dark' : 'light'}
        loading={<div className="editor-loading">Chargement de l'éditeur…</div>}
        options={{
          minimap: { enabled: false },
          fontSize: 14,
          lineNumbers: 'on',
          scrollBeyondLastLine: false,
          wordWrap: 'on',
          automaticLayout: true,
          tabSize: 2,
          padding: { top: 12 },
        }}
      />
    </div>
  );
}
