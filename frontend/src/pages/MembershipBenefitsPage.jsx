import { SectionIntro } from '../components/common'

export function MembershipBenefitsPage({ membershipLevels, membership }) {
  const currentLevelId = membership?.membershipLevel?.membershipLevelId
  const bookingsCompleted = membership?.completedBookingCount || 0
  const sortedLevels = [...membershipLevels].sort((a, b) => a.displayOrder - b.displayOrder)
  let nextLevel = null
  for (const level of sortedLevels) {
    if (level.requiredCompletedBookings > bookingsCompleted) {
      nextLevel = level
      break
    }
  }
  const progress = nextLevel ? Math.min(100, (bookingsCompleted / nextLevel.requiredCompletedBookings) * 100) : 100

  return (
    <section className="section limitWidth">
      <SectionIntro kicker="Membership" title="Your membership" text="Complete bookings to move up a tier. Eligible discounts are applied automatically at checkout." />
      <div className="membershipLayout">
        <aside className="membershipProgress" aria-live="polite">
          <span className="membershipEyebrow">Current standing</span>
          <h3>{membership?.membershipLevel?.levelName || 'Not enrolled'}</h3>
          <strong>{bookingsCompleted}</strong>
          <span className="membershipCountLabel">completed booking{bookingsCompleted === 1 ? '' : 's'}</span>
          {nextLevel ? (
            <div className="membershipProgressDetail">
              <div><span>Next: {nextLevel.levelName}</span><span>{Math.max(0, nextLevel.requiredCompletedBookings - bookingsCompleted)} to go</span></div>
              <div className="membershipProgressTrack"><span style={{ width: `${progress}%` }} /></div>
            </div>
          ) : <p className="membershipComplete">Highest tier reached</p>}
        </aside>

        <div className="membershipTiers">
          <div className="membershipTierHeading"><h3>Tier benefits</h3><span>{sortedLevels.length} tiers</span></div>
          <ol className="tierTimeline">
            {sortedLevels.map(level => {
              const isCurrent = level.membershipLevelId === currentLevelId
              const isUnlocked = level.requiredCompletedBookings <= bookingsCompleted
              return (
                <li key={level.membershipLevelId} className={`tierTimelineItem ${isCurrent ? 'current' : ''} ${isUnlocked ? 'unlocked' : ''}`}>
                  <div className="tierTimelineMarker" aria-hidden="true" />
                  <div className="tierTimelineContent">
                    <div className="tierTimelineTopline"><h4>{level.levelName}</h4>{isCurrent && <span>Current tier</span>}</div>
                    <strong>{level.discountPercent}% off</strong>
                    <p>{level.requiredCompletedBookings === 0 ? 'Starting tier' : `${level.requiredCompletedBookings} completed bookings required`}</p>
                    <ul>{splitBenefits(level.benefitDescription).map(benefit => <li key={benefit}>{benefit}</li>)}</ul>
                  </div>
                </li>
              )
            })}
          </ol>
        </div>
      </div>
    </section>
  )
}

function splitBenefits(description) {
  return (description || 'Standard field booking access.').split(/\n|•/).map(item => item.trim()).filter(Boolean)
}
