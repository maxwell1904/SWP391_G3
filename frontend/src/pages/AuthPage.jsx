import { Loader2, LogIn, LogOut } from 'lucide-react'
import { FieldControl, PasswordField, SectionIntro } from '../components/common'
import { EmailVerificationPanel } from '../features/account/components'

export function AuthPage({
  currentUser,
  authMode,
  setAuthMode,
  loginErrors,
  registerErrors,
  loginForm,
  registerForm,
  updateLoginForm,
  updateRegisterForm,
  showLoginPassword,
  setShowLoginPassword,
  showRegisterPassword,
  setShowRegisterPassword,
  login,
  register,
  loginLoading,
  registerLoading,
  logout,
  navigatePage,
  resendVerification
}) {
  const signedInDestination = currentUser?.role === 'Admin'
    ? { page: 'admin', title: 'Admin account ready', text: 'Manage fields, users, policies, promotions, reports, and system configuration.', action: 'Open admin console' }
    : currentUser?.role === 'Staff'
      ? { page: 'staff', title: 'Staff account ready', text: 'Continue to daily operations, walk-in bookings, payments, issues, and refunds.', action: 'Open staff workspace' }
      : { page: 'booking', title: 'Customer account ready', text: 'Reserve selected slots and track booking, payment, membership, and support updates from your account.', action: 'Continue booking' }

  return (
    <section id="login" className="section authSection">
      <SectionIntro
        kicker="Account"
        title={currentUser ? signedInDestination.title : 'Sign in before checkout'}
        text={currentUser
          ? signedInDestination.text
          : 'Customers need an account to hold a field and receive payment, cancellation, and refund updates.'}
      />
      <div className="authGrid">
        <article className="authPanel">
          {currentUser ? (
            <div className="signedInCard">
              <span>{currentUser.emailVerified ? 'Signed in' : 'Email verification needed'}</span>
              <h3>{currentUser.fullName}</h3>
              <p>{currentUser.role} account · {currentUser.emailVerified ? 'verified email' : 'unverified email'}</p>
              {!currentUser.emailVerified && (
                <EmailVerificationPanel
                  email={currentUser.email}
                  onResend={resendVerification}
                />
              )}
              <div className="buttonRow noMargin">
                <button className="primaryButton wide" onClick={() => navigatePage(signedInDestination.page)}>{signedInDestination.action}</button>
                <button className="ghostDarkButton wide" onClick={logout}>
                  <LogOut size={18} />
                  <span>Log out</span>
                </button>
              </div>
            </div>
          ) : (
            <>
              <div className="segmented">
                <button className={authMode === 'login' ? 'active' : ''} onClick={() => setAuthMode('login')}>Login</button>
                <button className={authMode === 'register' ? 'active' : ''} onClick={() => setAuthMode('register')}>Register</button>
              </div>
              {authMode === 'login' ? (
                <LoginForm
                  loginForm={loginForm}
                  loginErrors={loginErrors}
                  showLoginPassword={showLoginPassword}
                  setShowLoginPassword={setShowLoginPassword}
                  updateLoginForm={updateLoginForm}
                  login={login}
                  loginLoading={loginLoading}
                  onForgotPassword={() => navigatePage('forgotPassword')}
                />
              ) : (
                <RegisterForm
                  registerForm={registerForm}
                  registerErrors={registerErrors}
                  showRegisterPassword={showRegisterPassword}
                  setShowRegisterPassword={setShowRegisterPassword}
                  updateRegisterForm={updateRegisterForm}
                  register={register}
                  registerLoading={registerLoading}
                />
              )}
            </>
          )}
        </article>
      </div>
    </section>
  )
}

function LoginForm({ loginForm, loginErrors, showLoginPassword, setShowLoginPassword, updateLoginForm, login, loginLoading, onForgotPassword }) {
  return (
    <form
      className="formStack"
      onSubmit={event => {
        event.preventDefault()
        login()
      }}
    >
      <FieldControl label="Email or phone" error={loginErrors.emailOrPhone}>
        <input
          autoComplete="username"
          value={loginForm.emailOrPhone}
          onChange={event => updateLoginForm('emailOrPhone', event.target.value)}
        />
      </FieldControl>
      <PasswordField
        label="Password"
        value={loginForm.password}
        visible={showLoginPassword}
        error={loginErrors.password}
        autoComplete="current-password"
        onToggle={() => setShowLoginPassword(!showLoginPassword)}
        onChange={value => updateLoginForm('password', value)}
      />
      <div className="forgotPasswordRow">
        <button type="button" className="forgotPasswordLink" onClick={onForgotPassword}>
          Forgot password?
        </button>
      </div>
      <button type="submit" className="primaryButton wide" disabled={loginLoading}>
        {loginLoading ? <Loader2 className="spin" size={18} /> : <LogIn size={18} />}
        <span>{loginLoading ? 'Signing in…' : 'Login'}</span>
      </button>
    </form>
  )
}

function RegisterForm({ registerForm, registerErrors, showRegisterPassword, setShowRegisterPassword, updateRegisterForm, register, registerLoading }) {
  return (
    <form
      className="formStack"
      onSubmit={event => {
        event.preventDefault()
        register()
      }}
    >
      <FieldControl label="Full name" error={registerErrors.fullName}>
        <input
          autoComplete="name"
          value={registerForm.fullName}
          onChange={event => updateRegisterForm('fullName', event.target.value)}
        />
      </FieldControl>
      <FieldControl label="Email" error={registerErrors.email}>
        <input
          autoComplete="email"
          inputMode="email"
          value={registerForm.email}
          onChange={event => updateRegisterForm('email', event.target.value)}
        />
      </FieldControl>
      <FieldControl label="Phone" error={registerErrors.phone}>
        <input
          autoComplete="tel"
          inputMode="tel"
          value={registerForm.phone}
          onChange={event => updateRegisterForm('phone', event.target.value)}
        />
      </FieldControl>
      <PasswordField
        label="Password"
        value={registerForm.password}
        visible={showRegisterPassword}
        error={registerErrors.password}
        hint="At least 8 characters with uppercase, lowercase, number, and special character."
        autoComplete="new-password"
        onToggle={() => setShowRegisterPassword(!showRegisterPassword)}
        onChange={value => updateRegisterForm('password', value)}
      />
      <PasswordField
        label="Confirm password"
        value={registerForm.confirmPassword}
        visible={showRegisterPassword}
        error={registerErrors.confirmPassword}
        autoComplete="new-password"
        onToggle={() => setShowRegisterPassword(!showRegisterPassword)}
        onChange={value => updateRegisterForm('confirmPassword', value)}
      />
      <button type="submit" className="primaryButton wide" disabled={registerLoading}>
        {registerLoading ? <Loader2 className="spin" size={18} /> : null}
        <span>{registerLoading ? 'Creating account…' : 'Create account'}</span>
      </button>
    </form>
  )
}
