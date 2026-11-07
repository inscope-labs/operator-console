/**
 * OperationType helpers and closed-set validation.
 * Spec §8.4.3 — unknown values refused (UNKNOWN_OP).
 */

import { OPERATION_TYPES, type OperationType, isOperationType } from './types';

/** Operations that require a full publish-grade guard set. */
export const PUBLISH_GRADE_OPS: readonly OperationType[] = [
  'publish',
] as const;

/** Operations with repo-wide scope (own capability class). */
export const REPO_WIDE_OPS: readonly OperationType[] = [
  'halt',
  'resume',
] as const;

/** Highest-authority decision operation (own capability class + permanent audit). */
export const DECISION_OPS: readonly OperationType[] = [
  'decide',
] as const;

/** Read-oriented / non-mutating ops that still travel with a capability in some flows. */
export const LIGHT_OPS: readonly OperationType[] = [
  'doctor',
  'sync',
] as const;

export function assertOperationType(value: string): OperationType {
  if (!isOperationType(value)) {
    throw new Error(`UNKNOWN_OP: ${value}`);
  }
  return value;
}

export function isPublishGrade(op: OperationType): boolean {
  return (PUBLISH_GRADE_OPS as readonly string[]).includes(op);
}

export function isRepoWide(op: OperationType): boolean {
  return (REPO_WIDE_OPS as readonly string[]).includes(op);
}

export function isDecision(op: OperationType): boolean {
  return (DECISION_OPS as readonly string[]).includes(op);
}

export function allOperations(): readonly OperationType[] {
  return OPERATION_TYPES;
}
