"""Makes the shared drawing primitives importable. The M7 figures reuse doc/diagrams/m10/src
(draw, activity, check, the page skeleton) instead of keeping a second copy of them.

The M10 folder goes last on sys.path, so `build_figures` still resolves to this folder's registry."""

import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
SHARED = HERE.parent.parent / "m10" / "src"
if str(HERE) not in sys.path:
    sys.path.insert(0, str(HERE))
if str(SHARED) not in sys.path:
    sys.path.append(str(SHARED))
sys.dont_write_bytecode = True
