import { useState } from 'react'
import { SectionIntro, FieldControl } from '../components/common'

export function MembershipRulesPage({ membershipLevels, currentUser, createMembershipLevel, updateMembershipLevel }) {
  const isAdmin = currentUser?.role === 'Admin'
  const [isEditing, setIsEditing] = useState(false)
  const [editForm, setEditForm] = useState(emptyForm())

  if (!isAdmin) {
    return (
      <section className="section limitWidth">
        <SectionIntro kicker="Membership" title="Access denied" text="Only administrators can manage membership rules." />
      </section>
    )
  }

  function handleEdit(level) {
    setEditForm({
      membershipLevelId: level.membershipLevelId,
      levelName: level.levelName,
      requiredCompletedBookings: level.requiredCompletedBookings || 0,
      discountPercent: level.discountPercent || 0,
      benefitDescription: level.benefitDescription || '',
      displayOrder: level.displayOrder || 0,
      status: level.status || 'active',
      qualificationPeriod: level.qualificationPeriod || 'lifetime',
      requiredConsecutivePeriods: level.requiredConsecutivePeriods || 1
    })
    setIsEditing(true)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  function handleNew() {
    setEditForm(emptyForm())
    setIsEditing(true)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  function updateField(field, value) {
    setEditForm(prev => ({ ...prev, [field]: value }))
  }

  async function handleSave() {
    const payload = {
      ...editForm,
      requiredCompletedBookings: Number(editForm.requiredCompletedBookings),
      discountPercent: Number(editForm.discountPercent),
      displayOrder: Number(editForm.displayOrder),
      requiredConsecutivePeriods: Number(editForm.requiredConsecutivePeriods)
    }
    const result = editForm.membershipLevelId
      ? await updateMembershipLevel(editForm.membershipLevelId, payload)
      : await createMembershipLevel(payload)
    if (result) setIsEditing(false)
  }

  return (
    <section className="section limitWidth">
      <SectionIntro kicker="Membership" title="Tier rules" text="Define the thresholds and automatic discounts customers earn after completed bookings." />
      <div className="sectionContent">
        {isEditing ? (
          <div className="promoForm">
            <div className="formHeading">
              <div>
                <span>Tier editor</span>
                <h3>{editForm.membershipLevelId ? 'Edit membership tier' : 'Create membership tier'}</h3>
              </div>
              <p>Customers move up automatically when they complete enough bookings.</p>
            </div>
            <div className="promoFormGrid">
              <FieldControl label="Level Name">
                <input type="text" value={editForm.levelName} onChange={e => updateField('levelName', e.target.value)} placeholder="e.g. Gold" />
              </FieldControl>
              <FieldControl label="Required Bookings">
                <input type="number" value={editForm.requiredCompletedBookings} onChange={e => updateField('requiredCompletedBookings', e.target.value)} min="0" />
              </FieldControl>
              <FieldControl label="Default Discount (%)">
                <input type="number" step="0.5" value={editForm.discountPercent} onChange={e => updateField('discountPercent', e.target.value)} min="0" max="100" />
              </FieldControl>
              <FieldControl label="Tier position">
                <input type="number" value={editForm.displayOrder} onChange={e => updateField('displayOrder', e.target.value)} />
              </FieldControl>
              <FieldControl label="Qualification Period">
                <select value={editForm.qualificationPeriod} onChange={e => updateField('qualificationPeriod', e.target.value)}>
                  <option value="lifetime">Lifetime completed bookings</option>
                  <option value="monthly">Completed bookings this month</option>
                  <option value="weekly">Weekly consecutive streak</option>
                </select>
              </FieldControl>
              {editForm.qualificationPeriod === 'weekly' && (
                <FieldControl label="Consecutive Weeks Required">
                  <input type="number" value={editForm.requiredConsecutivePeriods} onChange={e => updateField('requiredConsecutivePeriods', e.target.value)} min="1" />
                </FieldControl>
              )}
              <FieldControl label="Status">
                <select value={editForm.status} onChange={e => updateField('status', e.target.value)}>
                  <option value="active">Active</option>
                  <option value="inactive">Inactive</option>
                </select>
              </FieldControl>
              <div className="fullWidth">
                <FieldControl label="Benefits Description (Shown to customers)">
                  <textarea value={editForm.benefitDescription} onChange={e => updateField('benefitDescription', e.target.value)} placeholder="List the benefits separated by newlines" rows="4" />
                </FieldControl>
              </div>
            </div>
            <div className="buttonRow">
              <button type="button" className="primaryButton" onClick={handleSave}>Save tier</button>
              <button type="button" className="secondaryButton" onClick={() => setIsEditing(false)}>Cancel</button>
            </div>
          </div>
        ) : (
          <div className="adminControls">
            <button type="button" className="primaryButton" onClick={handleNew}>Add membership tier</button>
          </div>
        )}

        <div className="tierAdminList">
          {[...membershipLevels].sort((a, b) => a.displayOrder - b.displayOrder).map((level, index) => (
            <article key={level.membershipLevelId} className={`tierAdminItem ${level.status === 'inactive' ? 'inactive' : ''}`}>
              <div className="tierAdminRank" aria-hidden="true">{index + 1}</div>
              <div className="tierAdminIdentity">
                <div className="tierAdminNameRow">
                  <h3>{level.levelName}</h3>
                  <span className={`promoStatusPill ${level.status}`}>{level.status}</span>
                </div>
                <p>{level.benefitDescription || 'Standard field booking access.'}</p>
              </div>
              <dl className="tierAdminMetrics">
                <div><dt>Threshold</dt><dd>{level.requiredCompletedBookings} bookings</dd></div>
                <div><dt>Rule</dt><dd>{level.qualificationPeriod === 'weekly' ? `${level.requiredConsecutivePeriods} weeks in a row` : level.qualificationPeriod || 'lifetime'}</dd></div>
                <div><dt>Discount</dt><dd>{level.discountPercent}% off</dd></div>
              </dl>
              <button type="button" className="ghostDarkButton" onClick={() => handleEdit(level)}>Edit tier</button>
            </article>
          ))}
          {membershipLevels.length === 0 && (
            <p className="emptyText" style={{ gridColumn: '1/-1' }}>No membership levels defined.</p>
          )}
        </div>
      </div>
    </section>
  )
}

function emptyForm() {
  return {
    levelName: '',
    requiredCompletedBookings: 0,
    discountPercent: 0,
    benefitDescription: '',
    displayOrder: 0,
    status: 'active',
    qualificationPeriod: 'lifetime',
    requiredConsecutivePeriods: 1
  }
}
