import { Badge, Button } from '@mantine/core'
import { formatDateTime, humanizeStatus } from '../../../utils/format'

export function IssueCaseList({ issues = [], resolutionNotes = {}, onNoteChange, onStart, onResolve, onReject }) {
  if (!issues.length) return <p className="emptyText">No issues reported.</p>

  return (
    <div className="supportCaseList">
      {issues.map(issue => {
        const active = issue.status === 'open' || issue.status === 'in_progress'
        const context = issue.bookingCode || issue.fieldName || issue.extraServiceName || 'General venue issue'
        const note = resolutionNotes[issue.issueId] || ''
        return (
          <article className="supportCase" key={issue.issueId}>
            <div className="supportCaseHeader">
              <div>
                <strong>{issue.title}</strong>
                <small>{context} · Reported by {issue.reporter}</small>
              </div>
              <Badge variant="light" color={issueStatusColor(issue.status)}>{humanizeStatus(issue.status)}</Badge>
            </div>
            <p>{issue.description || 'No description was provided.'}</p>
            <div className="supportCaseMeta">
              {issue.createdAt && <span>Opened {formatDateTime(issue.createdAt)}</span>}
              {issue.assignedStaff && <span>Assigned to {issue.assignedStaff}</span>}
              {issue.resolutionNote && <span>Resolution: {issue.resolutionNote}</span>}
            </div>
            {active && onResolve && (
              <div className="supportCaseActions">
                <label>
                  <span>Resolution note</span>
                  <input
                    value={note}
                    onChange={event => onNoteChange(issue.issueId, event.target.value)}
                    placeholder="Describe what was checked or fixed"
                  />
                </label>
                {issue.status === 'open' && (
                  <Button size="xs" variant="light" onClick={() => onStart(issue)}>Start work</Button>
                )}
                <Button size="xs" color="green" disabled={!note.trim()} onClick={() => onResolve(issue, note.trim())}>
                  Resolve
                </Button>
                {onReject && (
                  <Button size="xs" color="red" variant="light" disabled={!note.trim()} onClick={() => onReject(issue, note.trim())}>
                    Reject
                  </Button>
                )}
              </div>
            )}
          </article>
        )
      })}
    </div>
  )
}

function issueStatusColor(status) {
  if (status === 'resolved') return 'green'
  if (status === 'rejected') return 'red'
  if (status === 'in_progress') return 'blue'
  return 'yellow'
}
