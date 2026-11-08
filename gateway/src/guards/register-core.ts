/**
 * Side-effect import of every publish guard in pipeline order.
 * Import this module once from the Worker entry (index.ts).
 *
 * Order: G1 → G3 → G4 → G5 → G6 → G7 → G8 → G11 → G13 → G14 → G15
 */

import './g1-private';
import './g3-branch-shape';
import './g4-branch-exists';
import './g5-ancestry';
import './g6-non-empty-delta';
import './g7-no-merge-commits';
import './g8-denied-paths';
import './g11-secret-patterns';
import './g13-exact-sha';
import './g14-halt';
import './g15-submission-immutable';
