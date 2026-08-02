import { useState } from 'react'
import { MailCheck } from 'lucide-react'
import { useAutoDismiss } from '../../../hooks/useAutoDismiss'

export function EmailVerificationPanel({ email, onResend }) {
  const [sentNotice, setSentNotice] = useState(false)
  const [sending, setSending] = useState(false)

  useAutoDismiss(sentNotice, () => setSentNotice(false), 5000)

  const handleResend = async () => {
    setSending(true)
    try {
      const result = await onResend()
      if (result) setSentNotice(true)
    } finally {
      setSending(false)
    }
  }

  return (
    <div className="verificationPanel">
      <div>
        <MailCheck size={20} />
        <span>Email Verification Required</span>
      </div>
      <p>
        We have sent a verification link to your email address: <strong>{email}</strong>.
        Please check your inbox (and spam folder) to activate your account and enable online booking.
      </p>
      <div className="buttonRow noMargin">
        <button type="button" className="secondaryButton" disabled={sending} onClick={handleResend}>
          {sending ? 'Sending…' : sentNotice ? 'Verification email sent' : 'Resend verification email'}
        </button>
      </div>
    </div>
  )
}
