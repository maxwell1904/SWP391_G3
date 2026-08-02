import { useState } from 'react'
import api from '../../services/api'
import { resolveAssetUrl } from '../../utils/format'

export function ImageUploadField({ label = 'Image', value, onChange }) {
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState('')

  async function upload(event) {
    const file = event.target.files?.[0]
    if (!file) return
    setUploading(true)
    setError('')
    try {
      const body = new FormData()
      body.append('file', file)
      const response = await api.post('/admin/uploads/images', body)
      onChange(response.data.url)
    } catch (requestError) {
      setError(requestError.response?.data?.error || 'Could not upload this image.')
    } finally {
      setUploading(false)
      event.target.value = ''
    }
  }

  return (
    <div className="imageUploadField">
      <span className="fieldLabel">{label}</span>
      {value && <img src={resolveAssetUrl(value)} alt="Uploaded preview" width="640" height="360" />}
      <label className={uploading ? 'imageUploadButton disabled' : 'imageUploadButton'}>
        {uploading ? 'Uploading…' : value ? 'Replace image' : 'Upload image'}
        <input type="file" accept="image/jpeg,image/png,image/webp,image/gif" onChange={upload} disabled={uploading} />
      </label>
      <input
        type="url"
        value={value || ''}
        onChange={event => {
          setError('')
          onChange(event.target.value)
        }}
        placeholder="Or paste an HTTPS image URL"
        aria-label={`${label} URL`}
      />
      <small>JPEG, PNG, WebP or GIF, up to 5 MB.</small>
      {error && <small className="fieldError">{error}</small>}
    </div>
  )
}
