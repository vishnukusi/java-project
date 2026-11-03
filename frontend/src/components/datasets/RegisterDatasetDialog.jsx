import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/overlays'
import { Field, Input, Select } from '@/components/ui/input'
import { USE_MOCK } from '@/services'

export default function RegisterDatasetDialog({ open, onClose, onSubmit }) {
  const [name, setName] = useState('')
  const [format, setFormat] = useState('COCO')
  const [file, setFile] = useState(null)
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState('')

  const reset = () => { setName(''); setFormat('COCO'); setFile(null); setErr(''); setBusy(false) }
  const close = () => { reset(); onClose() }

  const submit = async (e) => {
    e.preventDefault()
    if (!name.trim()) return setErr('Dataset name is required.')
    setBusy(true)
    try { await onSubmit({ name: name.trim(), format, file }); reset() }
    catch (ex) { setErr(ex.message); setBusy(false) }
  }

  return (
    <Dialog
      open={open} onClose={close} title="Register dataset"
      description={USE_MOCK ? 'Mock mode: the file is not uploaded anywhere.' : 'Upload a COCO or YOLO dataset archive.'}
      footer={<>
        <Button variant="outline" onClick={close}>Cancel</Button>
        <Button type="submit" form="register-dataset" disabled={busy}>{busy ? 'Registering…' : 'Register'}</Button>
      </>}
    >
      <form id="register-dataset" onSubmit={submit} className="space-y-4">
        <Field label="Dataset name"><Input value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. warehouse-ppe-v4" autoFocus /></Field>
        <Field label="Annotation format">
          <Select value={format} onChange={(e) => setFormat(e.target.value)}>
            <option value="COCO">COCO (JSON annotations)</option>
            <option value="YOLO">YOLO (TXT labels)</option>
          </Select>
        </Field>
        <Field label="Dataset archive" hint="Zip containing images and annotations.">
          <Input type="file" accept=".zip,.tar,.gz" onChange={(e) => setFile(e.target.files?.[0] || null)} className="h-auto py-1.5 file:mr-3 file:rounded-md file:border-0 file:bg-slate-100 file:px-3 file:py-1 file:text-xs" />
        </Field>
        {err && <p className="text-xs text-red-600" role="alert">{err}</p>}
      </form>
    </Dialog>
  )
}
