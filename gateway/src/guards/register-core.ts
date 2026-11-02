/**
 * Import this module to register G1, G3, G4, G13 into the publish guard set.
 * Spec §10 — non-empty set required before any write.
 */
import './g1-private';
import './g3-branch-shape';
import './g4-branch-exists';
import './g13-exact-sha';
