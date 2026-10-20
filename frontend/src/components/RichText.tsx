import { Fragment } from 'react';

/** Affiche un texte simple : paragraphes séparés par une ligne vide, `code` entre accents graves. */
export function RichText({ text }: { text: string }) {
  return (
    <>
      {text.split(/\n\s*\n/).map((paragraph, i) => (
        <p key={i}>
          {paragraph.split(/(`[^`]+`)/).map((part, j) =>
            part.startsWith('`') && part.endsWith('`') ? (
              <code key={j}>{part.slice(1, -1)}</code>
            ) : (
              <Fragment key={j}>{part}</Fragment>
            ),
          )}
        </p>
      ))}
    </>
  );
}
