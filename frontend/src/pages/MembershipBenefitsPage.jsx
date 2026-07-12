import { SectionIntro } from '../components/common'

export function MembershipBenefitsPage({ membershipLevels, membership }) {
  const currentLevelId = membership?.membershipLevel?.membershipLevelId
  const bookingsCompleted = membership?.completedBookingCount || 0
  
  // Find next level
  const sortedLevels = [...membershipLevels].sort((a, b) => a.displayOrder - b.displayOrder)
  let nextLevel = null
  for (const level of sortedLevels) {
    if (level.requiredCompletedBookings > bookingsCompleted) {
      nextLevel = level
      break
    }
  }

  return (
    <section className="section limitWidth">
      <SectionIntro title="Membership Benefits" desc="Unlock exclusive discounts and perks by booking fields with us." />
      
      <div className="sectionContent" style={{ display: 'flex', flexDirection: 'column', gap: '2rem' }}>
        
        {/* Progress Tracker for Logged In Customer */}
        {membership && (
          <div className="card" style={{ padding: '1.5rem', background: 'var(--brand-faint)', border: '1px solid var(--brand-soft)' }}>
            <h3 style={{ margin: '0 0 1rem 0' }}>Your Progress</h3>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', marginBottom: '0.5rem' }}>
              <div>
                <span style={{ fontSize: '0.875rem', color: 'var(--muted)' }}>Current Tier:</span>
                <div style={{ fontSize: '1.25rem', fontWeight: 'bold', color: 'var(--brand)' }}>
                  {membership.membershipLevel?.levelName}
                </div>
              </div>
              <div style={{ textAlign: 'right' }}>
                <span style={{ fontSize: '0.875rem', color: 'var(--muted)' }}>Completed Bookings:</span>
                <div style={{ fontSize: '1.25rem', fontWeight: 'bold' }}>
                  {bookingsCompleted}
                </div>
              </div>
            </div>
            
            {nextLevel ? (
              <div style={{ marginTop: '1rem', paddingTop: '1rem', borderTop: '1px solid var(--border)' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.875rem', marginBottom: '0.5rem' }}>
                  <span>Progress to {nextLevel.levelName}</span>
                  <span>{nextLevel.requiredCompletedBookings - bookingsCompleted} more bookings needed</span>
                </div>
                <div style={{ height: '8px', background: 'var(--border)', borderRadius: '4px', overflow: 'hidden' }}>
                  <div style={{ height: '100%', background: 'var(--brand)', width: `${Math.min(100, (bookingsCompleted / nextLevel.requiredCompletedBookings) * 100)}%` }}></div>
                </div>
              </div>
            ) : (
              <div style={{ marginTop: '1rem', paddingTop: '1rem', borderTop: '1px solid var(--border)', fontSize: '0.875rem', color: 'var(--green)', fontWeight: 'bold' }}>
                You have reached the highest membership tier!
              </div>
            )}
          </div>
        )}

        {/* Benefits Comparison */}
        <div>
          <h3 style={{ margin: '0 0 1rem 0' }}>Tier Benefits</h3>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(250px, 1fr))', gap: '1rem' }}>
            {sortedLevels.map(level => {
              const isCurrent = level.membershipLevelId === currentLevelId
              return (
                <div key={level.membershipLevelId} className="card" style={{ padding: '1.5rem', border: isCurrent ? '2px solid var(--brand)' : '1px solid var(--border)', position: 'relative' }}>
                  {isCurrent && <span style={{ position: 'absolute', top: '-10px', right: '1rem', background: 'var(--brand)', color: 'white', padding: '0.2rem 0.6rem', borderRadius: '12px', fontSize: '0.75rem', fontWeight: 'bold' }}>Current</span>}
                  
                  <h4 style={{ margin: '0 0 0.5rem 0', fontSize: '1.25rem' }}>{level.levelName}</h4>
                  
                  <div style={{ fontSize: '2rem', fontWeight: 'bold', color: 'var(--brand)', margin: '1rem 0' }}>
                    {level.discountPercent}% <span style={{ fontSize: '1rem', color: 'var(--muted)', fontWeight: 'normal' }}>off</span>
                  </div>
                  
                  <p style={{ fontSize: '0.875rem', color: 'var(--muted)', margin: '0 0 1.5rem 0' }}>
                    {level.requiredCompletedBookings === 0 ? 'Default starting tier' : `Requires ${level.requiredCompletedBookings} completed bookings`}
                  </p>
                  
                  <div>
                    <strong style={{ fontSize: '0.875rem', display: 'block', marginBottom: '0.5rem' }}>Perks include:</strong>
                    <p style={{ margin: 0, fontSize: '0.875rem', whiteSpace: 'pre-wrap', lineHeight: '1.5' }}>
                      {level.benefitDescription || 'Standard field booking access.'}
                    </p>
                  </div>
                </div>
              )
            })}
          </div>
        </div>
      </div>
    </section>
  )
}
