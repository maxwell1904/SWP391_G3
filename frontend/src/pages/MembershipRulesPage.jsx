import { useState } from 'react'
import { SectionIntro, FieldControl } from '../components/common'

export function MembershipRulesPage({ membershipLevels, currentUser, createMembershipLevel, updateMembershipLevel }) {
  const isAdmin = currentUser?.role === 'Admin'
  const [isEditing, setIsEditing] = useState(false)
  const [editForm, setEditForm] = useState(emptyForm())

  if (!isAdmin) {
    return (
      <section className="section limitWidth">
        <SectionIntro title="Access Denied" desc="Only administrators can manage membership rules." />
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
      status: level.status || 'active'
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
      displayOrder: Number(editForm.displayOrder)
    }
    if (editForm.membershipLevelId) {
      await updateMembershipLevel(editForm.membershipLevelId, payload)
    } else {
      await createMembershipLevel(payload)
    }
    setIsEditing(false)
  }

  return (
    <section className="section limitWidth">
      <SectionIntro title="Membership Rules" desc="Configure membership tiers and benefits" />
      <div className="sectionContent">
        {isEditing ? (
          <div className="promoForm">
            <h3>{editForm.membershipLevelId ? 'Edit Membership Level' : 'New Membership Level'}</h3>
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
              <FieldControl label="Display Order">
                <input type="number" value={editForm.displayOrder} onChange={e => updateField('displayOrder', e.target.value)} />
              </FieldControl>
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
              <button className="primaryButton" onClick={handleSave}>Save</button>
              <button className="secondaryButton" onClick={() => setIsEditing(false)}>Cancel</button>
            </div>
          </div>
        ) : (
          <div className="adminControls">
            <button className="primaryButton" onClick={handleNew}>+ Add New Level</button>
          </div>
        )}

        <div className="promoCardGrid">
          {membershipLevels.map(level => (
            <div key={level.membershipLevelId} className={`promoCardFull ${level.status === 'inactive' ? 'inactive' : ''}`}>
              <div className="promoCardFullHeader">
                <span className="promoCodeBadge">{level.levelName}</span>
                <span className={`promoStatusPill ${level.status}`}>
                  {level.status}
                </span>
              </div>
              <div className="promoCardFullTitle">
                <div>
                  <h3>Requires {level.requiredCompletedBookings} bookings</h3>
                </div>
                <span className="promoDiscountBig">
                  {level.discountPercent}% off
                </span>
              </div>
              <p className="promoCardDesc">{level.benefitDescription}</p>
              
              <div className="promoCardActions">
                <button className="ghostDarkButton" onClick={() => handleEdit(level)}>Edit</button>
              </div>
            </div>
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
    status: 'active'
  }
}
