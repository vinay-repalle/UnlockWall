#!/usr/bin/env python3
"""Block Claude's file edits outside the UnlockWall folder (office laptop rule)."""
import json
import os
import sys

data = json.load(sys.stdin)
root = os.path.realpath(os.environ.get("CLAUDE_PROJECT_DIR") or data.get("cwd") or os.getcwd())
tool_input = data.get("tool_input") or {}
path = tool_input.get("file_path") or tool_input.get("notebook_path")
if not path:
    sys.exit(0)

full = os.path.realpath(os.path.join(data.get("cwd") or root, os.path.expanduser(path)))
if full == root or full.startswith(root + os.sep):
    sys.exit(0)

print(f"Blocked: {path} is outside {root}. Only edit files inside the UnlockWall folder.", file=sys.stderr)
sys.exit(2)
