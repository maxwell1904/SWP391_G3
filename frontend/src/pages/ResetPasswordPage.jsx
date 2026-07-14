import { KeyRound } from 'lucide-react'
import { PasswordField, SectionIntro } from '../components/common'

export function ResetPasswordPage({
  resetPassword,
  resetConfirmPassword,
  resetResult,
  resetLoading,
  resetErrors,
  showResetPassword,
  setShowResetPassword,
  resetPasswordToken,
  resetTokenChecking,
  updateResetForm,
  submitReset
}) {
  const isDone = resetResult?.status === 'success'

  return (
    <section id="reset-password" className="section authSection">
      <SectionIntro
        kicker="Password reset"
        title={resetTokenChecking ? 'Checking...' : isDone ? 'Password updated' : resetPasswordToken ? 'Set new password' : 'Invalid link'}
        text={resetTokenChecking
          ? 'Validating your reset link.'
          : isDone
            ? 'Your password has been reset successfully.'
            : resetPasswordToken
              ? 'Enter a new password for your account.'
              : 'This reset link is missing required information or has expired.'}
      />
      <div className="authGrid">
        <article className="authPanel">
          {resetTokenChecking ? (
            <div className="signedInCard">
              <span>Checking</span>
              <h3>Validating reset link</h3>
              <p>Please wait while we check your reset link.</p>
            </div>
          ) : isDone ? (
            <div className="signedInCard">
              <span>Success</span>
              <h3>Password reset complete</h3>
              <p>You can now sign in with your new password.</p>
              <div className="buttonRow noMargin">
                <button className="primaryButton wide" onClick={() => window.location.href = '/login'}>Go to login</button>
              </div>
            </div>
          ) : resetPasswordToken ? (
            <div className="formStack">
              <PasswordField
                label="New password"
                value={resetPassword}
                visible={showResetPassword}
                error={resetErrors.password}
                autoComplete="new-password"
                hint="At least 8 characters with uppercase, lowercase, number, and special character."
                onToggle={() => setShowResetPassword(!showResetPassword)}
                onChange={value => updateResetForm('resetPassword', value)}
              />
              <PasswordField
                label="Confirm new password"
                value={resetConfirmPassword}
                visible={showResetPassword}
                error={resetErrors.confirmPassword}
                autoComplete="new-password"
                onToggle={() => setShowResetPassword(!showResetPassword)}
                onChange={value => updateResetForm('resetConfirmPassword', value)}
              />
              <button className="primaryButton wide" disabled={resetLoading} onClick={submitReset}>
                <KeyRound size={18} />
                <span>{resetLoading ? 'Resetting...' : 'Reset password'}</span>
              </button>
            </div>
          ) : (
            <div className="signedInCard">
              <span>Invalid link</span>
              <h3>Cannot reset password</h3>
              <p>This reset link is invalid or expired. Please request a new one.</p>
              <div className="buttonRow noMargin">
                <button className="primaryButton wide" onClick={() => window.location.href = '/forgot-password'}>Request new link</button>
              </div>
            </div>
          )}
        </article>
      </div>
    </section>
  )
}
