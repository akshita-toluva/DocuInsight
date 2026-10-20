// Shared display wording for report types and statuses — change it here, it changes everywhere

export const TYPE_LABELS = {
  EXECUTIVE: 'Executive Summary',
  TECHNICAL: 'Technical Analysis',
  CUSTOM: 'Custom Report',
}

export const STATUS_CLASS = {
  COMPLETED: 'badge-ok',
  FAILED: 'badge-error',
  PENDING: 'badge-pending',
  PROCESSING: 'badge-pending',
}

// Helpers with fallbacks: an unknown value from the backend still displays
export function typeLabel(type) {
  return TYPE_LABELS[type] ?? type
}

export function statusClass(status) {
  return STATUS_CLASS[status] ?? 'badge-pending'
}