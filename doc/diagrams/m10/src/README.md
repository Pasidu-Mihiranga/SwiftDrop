# M10 diagram sources

The M10 figures (`../*.svg`, `../*.png`), the system figures (`../../system/`) and `doc/m10-design.pdf` are generated
by the Python scripts in this folder. Edit the scripts, never the SVG files.

| Script | Figures |
|---|---|
| `fig_usecases.py` | 01 use cases |
| `fig_er.py` | 02 data model (reporting_db and the assistant's Redis memory, including the PII vault) |
| `fig_architecture.py` | 03 architecture |
| `fig_integration.py` | 04 integration patterns 1-11 |
| `figures_flows.py` | 05-09 sequences, 11-12 class diagrams |
| `fig_activity.py` | 10 agent loop activity |
| `fig_system_er.py`, `fig_system_architecture.py`, `fig_system_eer.py` | system figures s1-s3 |
| `draw.py`, `activity.py`, `eer.py`, `attr_place.py` | drawing primitives shared by all figures |
| `build_figures.py` | the figure registry, titles and captions |
| `build_page.py`, `make_pdf.py`, `cdp_pdf.py` | the design page and the PDF |

## Rebuild

Needs Python 3.12+, Pillow and `websocket-client`, and Microsoft Edge (headless) for rasterising and printing.

```bash
cd doc/diagrams/m10/src
python check.py $(python -c "import build_figures as b; print(' '.join(b.FIGURES))")   # geometry check, expect 0 problems
bash render.sh                      # bw/*.svg and trimmed bw-png/*.png (or: bash render.sh 07-seq-where-is-my-parcel)
python make_pdf.py                  # m10-print.html
python cdp_pdf.py m10-print.html m10-design.pdf
```

Then copy the changed `bw/<id>.svg` and `bw-png/<id>.png` to `doc/diagrams/m10/` (system figures `s1`/`s3` go to
`doc/diagrams/system/system-er-diagram.*` and `system-eer-diagram.*`, `s2` to `system-architecture.*`) and
`m10-design.pdf` to `doc/`. The build output folders are git-ignored.

`check.py` catches crossing, overlapping and clipped geometry, but not text that wraps badly in a narrow space, so look
at every PNG you changed.
