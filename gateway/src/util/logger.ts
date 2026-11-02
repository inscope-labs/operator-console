/**
 * Structured logger — every string field passes through the redaction gate.
 */

import { redactRecord, redactString } from '../audit/redaction';

export type LogLevel = 'debug' | 'info' | 'warn' | 'error';

export interface LogFields {
  [key: string]: unknown;
}

function emit(level: LogLevel, message: string, fields?: LogFields): void {
  const safeMessage = redactString(message);
  const safeFields = fields ? redactRecord(fields as Record<string, unknown>) : {};
  const line = JSON.stringify({
    level,
    msg: safeMessage,
    ...safeFields,
    ts: new Date().toISOString(),
  });
  // eslint-disable-next-line no-console
  console.log(line);
}

export const logger = {
  debug: (message: string, fields?: LogFields) => emit('debug', message, fields),
  info: (message: string, fields?: LogFields) => emit('info', message, fields),
  warn: (message: string, fields?: LogFields) => emit('warn', message, fields),
  error: (message: string, fields?: LogFields) => emit('error', message, fields),
};
