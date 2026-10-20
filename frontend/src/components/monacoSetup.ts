/**
 * Monaco est intégré à l'application (aucun chargement depuis un CDN) : l'éditeur fonctionne
 * même sur un réseau d'établissement filtré. Seuls les fonctionnalités de l'éditeur et le langage SQL
 * sont embarqués, pas les services de langage lourds (TypeScript, CSS, HTML, JSON).
 */
import { loader } from '@monaco-editor/react';
import 'monaco-editor/features/register.all';
import * as monaco from 'monaco-editor/editor/editor.api';
import 'monaco-editor/languages/definitions/sql/register';
import EditorWorker from 'monaco-editor/editor/editor.worker?worker';

self.MonacoEnvironment = {
  getWorker: () => new EditorWorker(),
};

loader.config({ monaco });
