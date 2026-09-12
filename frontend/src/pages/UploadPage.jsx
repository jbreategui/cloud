import { useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { uploadImage } from '../api/client';
import Icon from '../components/Icon';

const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

export default function UploadPage() {
  const [file, setFile] = useState(null);
  const [preview, setPreview] = useState(null);
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [dragActive, setDragActive] = useState(false);
  const inputRef = useRef(null);
  const navigate = useNavigate();

  function pickFile(selected) {
    setError(null);
    if (!selected) return;
    if (!ALLOWED_TYPES.includes(selected.type)) {
      setError('Solo se permiten imágenes JPEG, PNG o WEBP.');
      return;
    }
    setFile(selected);
    setPreview(URL.createObjectURL(selected));
  }

  function clearFile() {
    setFile(null);
    setPreview(null);
    if (inputRef.current) inputRef.current.value = '';
  }

  function handleDrop(event) {
    event.preventDefault();
    setDragActive(false);
    pickFile(event.dataTransfer.files?.[0]);
  }

  async function handleSubmit(event) {
    event.preventDefault();
    if (!file) {
      setError('Selecciona una imagen primero.');
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await uploadImage(file);
      navigate('/');
    } catch (err) {
      setError(err.response?.data?.error ?? 'No se pudo subir la imagen. Intenta de nuevo.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="max-w-xl mx-auto px-4 sm:px-6 py-10">
      <div className="bg-white rounded-2xl border border-gray-200/70 shadow-sm p-6 sm:p-8">
        <h1 className="text-xl font-extrabold text-gray-900 mb-1">Crear pin</h1>
        <p className="text-sm text-gray-500 mb-6">Sube una imagen JPEG, PNG o WEBP para tu tablero.</p>

        <form onSubmit={handleSubmit} className="space-y-5">
          <div
            onDragOver={(e) => {
              e.preventDefault();
              setDragActive(true);
            }}
            onDragLeave={() => setDragActive(false)}
            onDrop={handleDrop}
            className={`relative rounded-2xl border-2 border-dashed transition-colors ${
              dragActive ? 'border-[#e60023] bg-red-50/60' : 'border-gray-300 hover:border-gray-400'
            }`}
          >
            <input
              ref={inputRef}
              type="file"
              accept="image/jpeg,image/png,image/webp"
              onChange={(e) => pickFile(e.target.files?.[0])}
              className="absolute inset-0 opacity-0 cursor-pointer disabled:cursor-default"
              disabled={!!preview}
            />

            {preview ? (
              <div className="relative p-3">
                <img src={preview} alt="Vista previa" className="w-full max-h-80 object-contain rounded-xl" />
                <button
                  type="button"
                  onClick={clearFile}
                  className="absolute top-5 right-5 w-9 h-9 rounded-full bg-black/60 hover:bg-black/75 text-white flex items-center justify-center"
                >
                  <Icon name="close" className="text-[20px]" />
                </button>
              </div>
            ) : (
              <div className="flex flex-col items-center justify-center gap-2 py-16 text-gray-500 pointer-events-none">
                <div className="w-14 h-14 rounded-full bg-gray-100 flex items-center justify-center">
                  <Icon name="cloud_upload" className="text-[28px] text-gray-500" />
                </div>
                <p className="text-sm font-semibold text-gray-700">Arrastra una imagen aquí</p>
                <p className="text-xs text-gray-400">o haz clic para elegir un archivo</p>
              </div>
            )}
          </div>

          {error && (
            <div className="flex items-center gap-2 text-sm text-[#ad081b] bg-red-50 rounded-lg px-3 py-2">
              <Icon name="error" className="text-[18px]" />
              {error}
            </div>
          )}

          <button
            type="submit"
            disabled={submitting || !file}
            className="w-full flex items-center justify-center gap-2 bg-[#e60023] text-white font-bold rounded-full py-3 hover:bg-[#ad081b] disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
          >
            {submitting ? (
              <>
                <Icon name="progress_activity" className="text-[20px] animate-spin" />
                Publicando…
              </>
            ) : (
              <>
                <Icon name="publish" className="text-[20px]" />
                Publicar
              </>
            )}
          </button>
        </form>
      </div>
    </div>
  );
}
