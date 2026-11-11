"""Lecture des fichiers data.sql des bases bac à sable (INSERT INTO t (cols) VALUES (...), (...);)."""
import re
from pathlib import Path

INSERT = re.compile(r'INSERT INTO (\w+) \(([^)]*)\) VALUES\s*', re.S)


def _parse_value(token):
    if token == 'NULL':
        return None
    if token == 'TRUE':
        return True
    if token == 'FALSE':
        return False
    if re.fullmatch(r'-?\d+', token):
        return int(token)
    if re.fullmatch(r'-?\d+\.\d+', token):
        return float(token)
    raise ValueError(f'Valeur inattendue : {token!r}')


def _parse_tuples(text, pos):
    """Lit les tuples à partir de pos jusqu'au ';' final. Renvoie (lignes, position après ';')."""
    rows, n = [], len(text)
    while True:
        while text[pos] in ' \n\r\t,':
            pos += 1
        if text[pos] == ';':
            return rows, pos + 1
        assert text[pos] == '(', text[pos:pos + 40]
        pos += 1
        row, token = [], ''
        while True:
            c = text[pos]
            if c == "'":
                pos += 1
                buf = []
                while True:
                    if text[pos] == "'":
                        if pos + 1 < n and text[pos + 1] == "'":
                            buf.append("'")
                            pos += 2
                            continue
                        pos += 1
                        break
                    buf.append(text[pos])
                    pos += 1
                row.append(''.join(buf))
                token = None
            elif c in ',)':
                if token is not None and token.strip():
                    row.append(_parse_value(token.strip()))
                token = ''
                pos += 1
                if c == ')':
                    break
            else:
                if token is not None:
                    token += c
                pos += 1
        rows.append(row)


def read_tables(path):
    """Renvoie {table: [dict(colonne=valeur)]} dans l'ordre du fichier."""
    text = Path(path).read_text(encoding='utf-8')
    tables = {}
    pos = 0
    while True:
        m = INSERT.search(text, pos)
        if not m:
            return tables
        table = m.group(1)
        columns = [c.strip() for c in m.group(2).split(',')]
        rows, pos = _parse_tuples(text, m.end())
        tables.setdefault(table, []).extend(dict(zip(columns, row)) for row in rows)
