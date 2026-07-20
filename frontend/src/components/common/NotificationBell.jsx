import { useState, useEffect, useRef } from 'react'
import { Bell } from 'lucide-react'

export function NotificationBell({ currentUser, notifications, onToggleRead, onMarkAllRead }) {
  const [open, setOpen] = useState(false)
  const ref = useRef(null)

  const unreadCount = notifications.filter(n => !n.read).length

  useEffect(() => {
    function handleClickOutside(e) {
      if (ref.current && !ref.current.contains(e.target)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  if (!currentUser) return null

  function formatTime(sentAt) {
    if (!sentAt) return ''
    const date = new Date(sentAt)
    const now = new Date()
    const diffMs = now - date
    const diffMins = Math.floor(diffMs / 60000)
    if (diffMins < 1) return 'Just now'
    if (diffMins < 60) return `${diffMins}m ago`
    const diffHours = Math.floor(diffMins / 60)
    if (diffHours < 24) return `${diffHours}h ago`
    return date.toLocaleDateString('vi-VN')
  }

  const typeIcon = {
    booking_confirmed: '✅',
    booking_cancelled: '❌',
    payment_received: '💳',
    refund_processed: '💰',
    check_in_reminder: '⏰',
    system: '🔔',
  }

  return (
    <div className="notificationBellWrapper" ref={ref}>
      <button
        className="notificationBellButton"
        onClick={() => setOpen(o => !o)}
        aria-label={`Notifications (${unreadCount} unread)`}
        id="notification-bell-btn"
      >
        <Bell size={20} />
        {unreadCount > 0 && (
          <span className="notificationBadge">{unreadCount > 99 ? '99+' : unreadCount}</span>
        )}
      </button>

      {open && (
        <div className="notificationDropdown" role="dialog" aria-label="Notifications">
          <div className="notificationDropdownHeader">
            <h4>Notifications</h4>
            {unreadCount > 0 && (
              <button
                className="markAllReadBtn"
                onClick={() => onMarkAllRead(currentUser.userId)}
              >
                Mark all read
              </button>
            )}
          </div>

          <div className="notificationList">
            {notifications.length === 0 && (
              <div className="notificationEmpty">
                <span>🔕</span>
                <p>No notifications yet</p>
              </div>
            )}
            {notifications.map(n => (
              <button
                key={n.notificationId}
                className={`notificationItem ${n.read ? 'read' : 'unread'}`}
                onClick={() => onToggleRead(n.notificationId)}
                id={`notification-${n.notificationId}`}
              >
                <span className="notificationTypeIcon">{typeIcon[n.type] || '🔔'}</span>
                <div className="notificationContent">
                  <p className="notificationTitle">{n.title}</p>
                  <p className="notificationMessage">{n.message}</p>
                  <span className="notificationTime">{formatTime(n.sentAt)}</span>
                </div>
                {!n.read && <span className="unreadDot" aria-label="Unread" />}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
