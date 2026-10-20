#!/usr/bin/env python3
"""Creates small synthetic disc images for the emulator smoke test.

None of these is a real game. They exercise the scanner and Dolphin's DiscIO validation:
  homebrew_test_gc.iso  - GameCube header (ID GTST01) that Dolphin can open -> "GAME LAIN"
  broken_header.wbfs    - WBFS magic with an invalid sector shift      -> "TIDAK VALID"
  random_bytes.iso      - random data                                   -> "TIDAK VALID"
  readme.txt            - not a disc image, must be ignored by the scanner
"""
import os
import struct
import sys

out = sys.argv[1] if len(sys.argv) > 1 else "test-discs"
os.makedirs(out, exist_ok=True)

# GameCube disc header: game ID, disc no., version, magic 0xC2339F3D at 0x1C, title at 0x20.
size = 2 * 1024 * 1024
gc = bytearray(size)
gc[0:6] = b"GTST01"
gc[0x1C:0x20] = struct.pack(">I", 0xC2339F3D)
title = b"CODBO SMOKE TEST DISC"
gc[0x20:0x20 + len(title)] = title
# Apploader header at 0x2440 (date string) so DiscIO can read the apploader date.
gc[0x2440:0x2440 + 10] = b"2026/10/20"
with open(os.path.join(out, "homebrew_test_gc.iso"), "wb") as f:
    f.write(gc)

wbfs = bytearray(64 * 1024)
wbfs[0:4] = b"WBFS"
wbfs[8] = 2  # hd sector shift 2 -> invalid (must be >= 9)
with open(os.path.join(out, "broken_header.wbfs"), "wb") as f:
    f.write(wbfs)

with open(os.path.join(out, "random_bytes.iso"), "wb") as f:
    f.write(os.urandom(256 * 1024))

with open(os.path.join(out, "readme.txt"), "w") as f:
    f.write("not a disc\n")

print("test discs written to", out)
