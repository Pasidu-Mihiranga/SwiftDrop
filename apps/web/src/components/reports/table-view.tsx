interface Props {
  caption: string;
  columns: { label: string; numeric?: boolean }[];
  rows: (string | number)[][];
}

/** The accessible twin of a chart: every value it plots, as a table, one click away. */
export function TableView({ caption, columns, rows }: Props) {
  return (
    <details className="mt-4 text-sm">
      <summary className="cursor-pointer select-none text-brand-700 hover:underline">Show as table</summary>
      <div className="mt-3 max-h-96 overflow-auto rounded-lg border border-slate-200">
        <table className="w-full border-collapse">
          <caption className="sr-only">{caption}</caption>
          <thead className="sticky top-0 bg-slate-50">
            <tr>
              {columns.map((column) => (
                <th
                  key={column.label}
                  scope="col"
                  className={`px-3 py-2 font-medium text-slate-600 ${column.numeric ? "text-right" : "text-left"}`}
                >
                  {column.label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {rows.map((row) => (
              <tr key={String(row[0])}>
                {row.map((cell, i) => (
                  <td
                    key={columns[i]?.label ?? i}
                    className={`px-3 py-1.5 ${columns[i]?.numeric ? "text-right tabular-nums" : "text-left"}`}
                  >
                    {cell}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </details>
  );
}
