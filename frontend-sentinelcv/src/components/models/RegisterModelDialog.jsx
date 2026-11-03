import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/overlays'
import { Field, Input } from '@/components/ui/input'
import { USE_MOCK } from '@/services'

export default function RegisterModelDialog({ open, onClose, onSubmit }) {
  const [name, setName] = useState('')
  const [file, setFile] = useState(null)
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState('')

  const reset = () => { setName(''); setFile(null); setErr(''); setBusy(false) }
  const close = () => { reset(); onClose() }

  const submit = async (e) => {
    e.preventDefault()
    const finalName = name.trim() || file?.name
    if (!finalName) return setErr('Provide a model name or choose an .onnx file.')
    setBusy(true)
    try { await onSubmit({ name: finalName, file }); reset() }
    catch (ex) { setErr(ex.message); setBusy(false) }
  }

  return (
    <Dialog
      open={open} onClose={close} title="Register model"
      description={USE_MOCK ? 'Mock mode: the file is not uploaded anywhere.' : 'Upload an ONNX model file.'}
      footer={<>
        <Button variant="outline" onClick={close}>Cancel</Button>
        <Button type="submit" form="register-model" disabled={busy}>{busy ? 'Registering…' : 'Register'}</Button>
      </>}
    >
      <form id="register-model" onSubmit={submit} className="space-y-4">
        <Field label="Model file" hint="ONNX is the supported format.">
          <Input type="file" accept=".onnx" onChange={(e) => { const f = e.target.files?.[0] || null; setFile(f); if (f && !name) setName(f.name) }}
            className="h-auto py-1.5 file:mr-3 file:rounded-md file:border-0 file:bg-slate-100 file:px-3 file:py-1 file:text-xs" />
        </Field>
        <Field label="Display name"><Input value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. yolov8n-ppe.onnx" /></Field>
        {err && <p className="text-xs text-red-600" role="alert">{err}</p>}
      </form>
    </Dialog>
  )
}
