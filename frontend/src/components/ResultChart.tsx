import { Chart, registerables, type ChartConfiguration } from 'chart.js';
import { useEffect, useMemo, useRef } from 'react';
import type { ChartSpec, QueryResult } from '../api/types';
import { useColorScheme } from './useColorScheme';

Chart.register(...registerables);

/** Au-delà, les couleurs catégorielles ne sont plus distinguables : les parts suivantes sont regroupées. */
const MAX_SLICES = 8;
const MAX_POINTS = 60;

interface Series {
  name: string;
  values: (number | null)[];
}

type ChartData = { labels: string[]; series: Series[]; notes: string[] } | { error: string };

/**
 * Les `labelColumns` premières colonnes donnent le libellé de chaque point,
 * les colonnes suivantes dont toutes les valeurs sont numériques deviennent des séries.
 */
export function buildChartData(result: QueryResult, spec: ChartSpec): ChartData {
  const labelCount = spec.labelColumns;
  if (result.rows.length === 0) return { error: 'Aucune ligne à représenter.' };
  if (result.columns.length <= labelCount) {
    return {
      error: `Le graphique attend ${labelCount} colonne${labelCount > 1 ? 's' : ''} de libellé suivie${labelCount > 1 ? 's' : ''} d'au moins une colonne numérique.`,
    };
  }
  const notes: string[] = [];
  const rows = result.rows.slice(0, MAX_POINTS);
  if (result.rows.length > MAX_POINTS) notes.push(`Seules les ${MAX_POINTS} premières lignes sont représentées.`);

  const series: Series[] = [];
  for (let column = labelCount; column < result.columns.length; column++) {
    const values = rows.map((row) => row[column]);
    const numeric = values.every((v) => v === null || typeof v === 'number') && values.some((v) => v !== null);
    if (numeric) series.push({ name: result.columns[column], values: values as (number | null)[] });
  }
  if (series.length === 0) {
    return { error: 'Aucune colonne numérique à tracer après les colonnes de libellé.' };
  }
  const labels = rows.map((row) =>
    row
      .slice(0, labelCount)
      .map((v) => (v === null ? '(vide)' : String(v)))
      .join(' · '),
  );
  return { labels, series, notes };
}

function readTokens() {
  const style = getComputedStyle(document.documentElement);
  const token = (name: string) => style.getPropertyValue(name).trim();
  return {
    series: Array.from({ length: MAX_SLICES }, (_, i) => token(`--series-${i + 1}`)),
    text: token('--text'),
    muted: token('--muted'),
    grid: token('--border'),
    surface: token('--surface'),
  };
}

function foldSlices(labels: string[], values: (number | null)[]) {
  if (labels.length <= MAX_SLICES) return { labels, values };
  const rest = values.slice(MAX_SLICES - 1).reduce<number>((sum, v) => sum + (v ?? 0), 0);
  return {
    labels: [...labels.slice(0, MAX_SLICES - 1), 'Autres'],
    values: [...values.slice(0, MAX_SLICES - 1), rest],
  };
}

function buildConfig(spec: ChartSpec, labels: string[], series: Series[], labelName: string): ChartConfiguration {
  const t = readTokens();
  const common = {
    responsive: true,
    maintainAspectRatio: false,
    locale: 'fr-FR',
    animation: { duration: 250 },
  };

  if (spec.type === 'PIE' || spec.type === 'DOUGHNUT') {
    const folded = foldSlices(labels, series[0].values);
    return {
      type: spec.type === 'PIE' ? 'pie' : 'doughnut',
      data: {
        labels: folded.labels,
        datasets: [
          {
            label: series[0].name,
            data: folded.values,
            backgroundColor: folded.labels.map((_, i) => t.series[i]),
            borderColor: t.surface,
            borderWidth: 2,
            hoverOffset: 6,
          },
        ],
      },
      options: {
        ...common,
        plugins: {
          legend: { position: 'right', labels: { color: t.text, boxWidth: 12, boxHeight: 12, padding: 12 } },
        },
      },
    };
  }

  const horizontal = spec.type === 'HORIZONTAL_BAR';
  const multiple = series.length > 1;
  const valueAxis = {
    beginAtZero: true,
    grid: { color: t.grid },
    border: { display: false },
    ticks: { color: t.muted },
    title: { display: !multiple, text: series[0].name, color: t.muted },
  };
  const categoryAxis = {
    grid: { display: false },
    border: { color: t.grid },
    ticks: { color: t.muted, autoSkip: true, maxRotation: 45 },
    title: { display: true, text: labelName, color: t.muted },
  };

  return {
    type: spec.type === 'LINE' ? 'line' : 'bar',
    data: {
      labels,
      datasets: series.map((s, i) => ({
        label: s.name,
        data: s.values,
        backgroundColor: t.series[i % MAX_SLICES],
        borderColor: t.series[i % MAX_SLICES],
        ...(spec.type === 'LINE'
          ? { borderWidth: 2, pointRadius: 4, pointHoverRadius: 6, tension: 0, fill: false }
          : { borderRadius: 4, borderSkipped: 'start' as const, maxBarThickness: 48 }),
      })),
    },
    options: {
      ...common,
      indexAxis: horizontal ? 'y' : 'x',
      interaction: { mode: 'index', intersect: false },
      scales: horizontal ? { x: valueAxis, y: categoryAxis } : { x: categoryAxis, y: valueAxis },
      plugins: {
        legend: { display: multiple, labels: { color: t.text, boxWidth: 12, boxHeight: 12 } },
      },
    },
  } as ChartConfiguration;
}

export function ResultChart({ result, spec }: { result: QueryResult; spec: ChartSpec }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const colorScheme = useColorScheme();
  const data = useMemo(() => buildChartData(result, spec), [result, spec]);

  useEffect(() => {
    if (!canvasRef.current || 'error' in data) return;
    const labelName = result.columns.slice(0, spec.labelColumns).join(' · ');
    const chart = new Chart(canvasRef.current, buildConfig(spec, data.labels, data.series, labelName));
    return () => chart.destroy();
  }, [data, spec, result.columns, colorScheme]);

  if ('error' in data) {
    return <p className="chart-message muted">{data.error}</p>;
  }
  return (
    <div className="chart">
      <div className="chart-canvas">
        <canvas
          ref={canvasRef}
          role="img"
          aria-label={`Graphique : ${data.series.map((s) => s.name).join(', ')} par ${result.columns
            .slice(0, spec.labelColumns)
            .join(' et ')}`}
        />
      </div>
      {data.notes.map((note) => (
        <p key={note} className="chart-message muted">
          {note}
        </p>
      ))}
    </div>
  );
}
