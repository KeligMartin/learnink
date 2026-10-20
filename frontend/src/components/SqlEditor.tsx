import Editor, { type OnMount } from '@monaco-editor/react';
import './monacoSetup';
import type { editor, Position } from 'monaco-editor/editor/editor.api';
import { useEffect, useRef } from 'react';
import type { SchemaDescription } from '../api/types';
import { useColorScheme } from './useColorScheme';

const SQL_KEYWORDS = [
  'SELECT', 'FROM', 'WHERE', 'AND', 'OR', 'NOT', 'IN', 'IS NULL', 'IS NOT NULL', 'LIKE', 'BETWEEN',
  'JOIN', 'INNER JOIN', 'LEFT JOIN', 'RIGHT JOIN', 'ON', 'GROUP BY', 'HAVING', 'ORDER BY', 'ASC', 'DESC',
  'LIMIT', 'DISTINCT', 'AS', 'COUNT', 'SUM', 'AVG', 'MIN', 'MAX', 'ROUND', 'YEAR', 'MONTH', 'EXISTS',
  'UNION', 'WITH', 'CASE', 'WHEN', 'THEN', 'ELSE', 'END', 'OVER', 'PARTITION BY', 'RANK',
];

interface Props {
  value: string;
  onChange: (value: string) => void;
  onRun: () => void;
  schema?: SchemaDescription;
}

export function SqlEditor({ value, onChange, onRun, schema }: Props) {
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

    // Autocomplétion : mots-clés SQL, tables et colonnes de la base bac à sable
    disposables.current.push(
      monaco.languages.registerCompletionItemProvider('sql', {
        provideCompletionItems: (model: editor.ITextModel, position: Position) => {
          const word = model.getWordUntilPosition(position);
          const range = {
            startLineNumber: position.lineNumber,
            endLineNumber: position.lineNumber,
            startColumn: word.startColumn,
            endColumn: word.endColumn,
          };
          const { Keyword, Class, Field } = monaco.languages.CompletionItemKind;
          const tables = schemaRef.current?.tables ?? [];
          return {
            suggestions: [
              ...SQL_KEYWORDS.map((k) => ({ label: k, kind: Keyword, insertText: k, range })),
              ...tables.map((t) => ({ label: t.name, kind: Class, insertText: t.name, detail: 'table', range })),
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
        defaultLanguage="sql"
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
