import { useState } from 'react'
import { MailCheck } from 'lucide-react'

export function EmailVerificationPanel({ email, onResend }) {
  const [sentNotice, setSentNotice] = useState(false)

  const handleResend = async () => {
    await onResend()
    setSentNotice(true)
    setTimeout(() => setSentNotice(false), 5000)
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
        <button className="secondaryButton" onClick={handleResend}>
          {sentNotice ? 'Verification email sent' : 'Resend verification email'}
        </button>
      </div>
    </div>
  )
}
