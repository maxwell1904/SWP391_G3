import { SectionIntro } from '../components/common'

export function VerifyEmailPage({ verifyResult, currentUser, navigatePage, resendVerification }) {
  return (
    <section id="verify-email" className="section authSection">
      <SectionIntro
        kicker="Email verification"
        title={verifyResult.status === 'success' ? 'Email verified' : 'Verify your email'}
        text={verifyResult.message || 'Checking the verification link.'}
      />
      <div className="authGrid">
        <article className="authPanel">
          <div className="signedInCard">
            <span>{verifyResult.status === 'error' ? 'Needs attention' : verifyResult.status === 'success' ? 'Verified' : 'Checking'}</span>
            <h3>{verifyResult.message || 'Verifying email link'}</h3>
            <p>{verifyResult.status === 'success' ? 'You can continue booking online.' : 'If the link has expired, resend it from your account page.'}</p>
            <div className="buttonRow noMargin">
              <button className="primaryButton wide" onClick={() => navigatePage(currentUser ? 'booking' : 'login')}>
                {currentUser ? 'Continue booking' : 'Back to login'}
              </button>
              {currentUser && !currentUser.emailVerified && (
                <button className="ghostDarkButton wide" onClick={resendVerification}>Resend email</button>
              )}
            </div>
          </div>
        </article>
      </div>
    </section>
  )
}
