import { ArrowLeft, Mail } from 'lucide-react'
import { FieldControl, SectionIntro } from '../components/common'

export function ForgotPasswordPage({
  forgotEmail,
  setForgotEmail,
  forgotSent,
  forgotLoading,
  forgotError,
  sendResetLink,
  navigatePage
}) {
  return (
    <section id="forgot-password" className="section authSection">
      <SectionIntro
        kicker="Password reset"
        title={forgotSent ? 'Check your inbox' : 'Forgot password'}
        text={forgotSent
          ? 'If the email is registered, a reset link is on its way.'
          : 'Enter your email and we will send a reset link.'}
      />
      <div className="authGrid">
        <article className="authPanel">
          <div className="formStack">
            <FieldControl label="Email" error={forgotError}>
              <input
                type="email"
                autoComplete="email"
                value={forgotEmail}
                disabled={forgotSent}
                onChange={event => setForgotEmail(event.target.value)}
              />
            </FieldControl>
            {forgotSent ? (
              <button className="primaryButton wide" onClick={() => navigatePage('login')}>
                <ArrowLeft size={18} />
                <span>Back to login</span>
              </button>
            ) : (
              <button className="primaryButton wide" disabled={forgotLoading} onClick={sendResetLink}>
                <Mail size={18} />
                <span>{forgotLoading ? 'Sending...' : 'Send reset link'}</span>
              </button>
            )}
            <button className="ghostDarkButton wide" onClick={() => navigatePage('login')}>
              <ArrowLeft size={18} />
              <span>Back to login</span>
            </button>
          </div>
        </article>
      </div>
    </section>
  )
}
