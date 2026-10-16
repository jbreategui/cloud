import { useEffect, useRef, useState } from 'react';
import Masonry from 'react-masonry-css';
import { Link } from 'react-router-dom';
import { likeImage, listImages, retryBlur } from '../api/client';
import Icon from '../components/Icon';

const breakpoints = { default: 5, 1280: 4, 1024: 3, 640: 2, 400: 1 };

function SkeletonGrid() {
  const heights = [220, 300, 260, 340, 240, 290, 320, 250, 270, 310];
  return (
    <Masonry breakpointCols={breakpoints} className="flex gap-4" columnClassName="flex flex-col gap-4">
      {heights.map((h, i) => (
        <div key={i} className="skeleton rounded-2xl" style={{ height: h }} />
      ))}
    </Masonry>
  );
}

function EmptyState() {
  return (
    <div className="flex flex-col items-center justify-center text-center py-24 px-4">
      <div className="w-20 h-20 rounded-full bg-gray-100 flex items-center justify-center mb-4">
        <Icon name="add_photo_alternate" className="text-[40px] text-gray-400" />
      </div>
      <h2 className="text-lg font-bold text-gray-900 mb-1">Aún no hay imágenes</h2>
      <p className="text-sm text-gray-500 mb-5">Sube la primera y empieza tu tablero.</p>
      <Link
        to="/upload"
        className="inline-flex items-center gap-2 bg-[#e60023] text-white font-semibold text-sm rounded-full px-5 py-2.5 hover:bg-[#ad081b] transition-colors"
      >
        <Icon name="add" className="text-[20px]" />
        Subir imagen
      </Link>
    </div>
  );
}

function SensitiveOverlay({ image, onReveal, onRetry }) {
  const [retrying, setRetrying] = useState(false);
  const [retryError, setRetryError] = useState(null);
  const status = image.processingStatus;

  async function handleRetry(event) {
    event.stopPropagation();
    setRetrying(true);
    setRetryError(null);
    try {
      const updated = await onRetry(image.filename);
      if (updated.processingStatus !== 'DONE') {
        setRetryError('Sigue sin poder generarse. Intenta más tarde.');
      }
    } catch {
      setRetryError('No se pudo reintentar. Revisa tu conexión.');
    } finally {
      setRetrying(false);
    }
  }

  return (
    <div
      role="button"
      tabIndex={0}
      onClick={onReveal}
      onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && onReveal()}
      className="absolute inset-0 flex flex-col items-center justify-center gap-2 px-4 text-center text-white bg-black/35 cursor-pointer"
    >
      <Icon name="visibility_off" className="text-[32px] drop-shadow" />
      <p className="text-sm font-bold drop-shadow">Contenido sensible</p>

      {status === 'DONE' && <p className="text-xs text-white/85">Haz clic para ver la imagen</p>}

      {status === 'PENDING' && (
        <p className="flex items-center gap-1 text-xs text-white/85">
          <Icon name="progress_activity" className="text-[14px] animate-spin" />
          Generando vista previa…
        </p>
      )}

      {status === 'FAILED' && <p className="text-xs text-white/85">No se pudo generar la vista previa.</p>}

      {status !== 'DONE' && (
        <div className="flex flex-wrap items-center justify-center gap-2 mt-1">
          <button
            type="button"
            onClick={handleRetry}
            disabled={retrying}
            className="inline-flex items-center gap-1 rounded-full bg-white text-gray-900 text-xs font-semibold px-3 py-1.5 hover:bg-gray-100 disabled:opacity-60"
          >
            <Icon name={retrying ? 'progress_activity' : 'refresh'} className={`text-[16px] ${retrying ? 'animate-spin' : ''}`} />
            {retrying ? 'Reintentando…' : 'Reintentar'}
          </button>
          <span className="text-xs font-semibold underline underline-offset-2">Ver imagen</span>
        </div>
      )}

      {retryError && <p className="text-xs font-medium text-red-100 bg-black/40 rounded-md px-2 py-1">{retryError}</p>}
    </div>
  );
}

function ImageCard({ image, onLike, onRetry }) {
  const [burstId, setBurstId] = useState(0);
  const [bouncing, setBouncing] = useState(false);
  const [revealed, setRevealed] = useState(false);
  const bounceTimeoutRef = useRef(null);

  const hidden = image.sensitive && !revealed;
  const blurredReady = image.processingStatus === 'DONE' && image.blurredUrl;

  function triggerLike() {
    setBurstId((id) => id + 1);
    setBouncing(true);
    clearTimeout(bounceTimeoutRef.current);
    bounceTimeoutRef.current = setTimeout(() => setBouncing(false), 500);
    onLike(image.filename);
  }

  return (
    <figure className="group relative rounded-2xl overflow-hidden bg-white border border-gray-200/70 shadow-sm hover:shadow-xl transition-shadow duration-200">
      <div className="relative overflow-hidden select-none">
        {!hidden && (
          <img
            src={image.url}
            alt={image.originalFilename}
            loading="lazy"
            onDoubleClick={triggerLike}
            className="w-full block transition-transform duration-300 group-hover:scale-105"
          />
        )}

        {hidden && blurredReady && (
          <img src={image.blurredUrl} alt="Contenido sensible" loading="lazy" className="w-full block" />
        )}

        {/* Sin copia desenfocada (procesando o fallida) nunca se muestra el original sin pedirlo. */}
        {hidden && !blurredReady && <div className="w-full h-64 bg-gradient-to-br from-gray-500 to-gray-700" />}

        <div className="absolute inset-0 bg-gradient-to-t from-black/60 via-black/0 to-black/0 opacity-0 group-hover:opacity-100 transition-opacity duration-200 pointer-events-none" />

        {hidden && <SensitiveOverlay image={image} onReveal={() => setRevealed(true)} onRetry={onRetry} />}

        {image.sensitive && revealed && (
          <button
            type="button"
            onClick={() => setRevealed(false)}
            aria-label="Ocultar contenido sensible"
            className="absolute top-3 left-3 w-10 h-10 rounded-full bg-white/90 hover:bg-white flex items-center justify-center shadow-md backdrop-blur-sm opacity-0 group-hover:opacity-100 transition-opacity"
          >
            <Icon name="visibility_off" className="text-[20px] text-gray-700" />
          </button>
        )}

        {burstId > 0 && (
          <div
            key={burstId}
            className="absolute inset-0 flex items-center justify-center pointer-events-none"
          >
            <Icon
              name="favorite"
              fill
              className="animate-heart-burst text-white text-[84px] drop-shadow-[0_4px_12px_rgba(0,0,0,0.35)]"
            />
          </div>
        )}

        <button
          type="button"
          onClick={triggerLike}
          aria-label="Dar like"
          className={`absolute top-3 right-3 w-10 h-10 rounded-full flex items-center justify-center shadow-md backdrop-blur-sm transition-colors ${
            bouncing ? 'bg-[#e60023]' : 'bg-white/90 opacity-0 group-hover:opacity-100 hover:bg-white'
          }`}
        >
          <Icon
            name="favorite"
            fill={bouncing}
            className={`text-[20px] ${bouncing ? 'text-white animate-like-bounce' : 'text-gray-700'}`}
          />
        </button>

        <div className="hidden sm:flex items-center gap-1 absolute bottom-2.5 left-3 text-white text-xs font-semibold opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none">
          <Icon name="favorite" fill className="text-[15px]" />
          {image.likes}
        </div>
      </div>

      <div className="flex items-center justify-end px-3 py-2 sm:hidden">
        <span className="flex items-center gap-1 text-xs font-semibold text-gray-600">
          <Icon name="favorite" className="text-[16px] text-[#e60023]" fill />
          {image.likes}
        </span>
      </div>
    </figure>
  );
}

export default function GalleryPage() {
  const [images, setImages] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    listImages()
      .then(setImages)
      .catch(() => setError('No se pudieron cargar las imágenes.'))
      .finally(() => setLoading(false));
  }, []);

  async function handleLike(filename) {
    const previous = images;
    setImages((current) =>
      current.map((img) => (img.filename === filename ? { ...img, likes: img.likes + 1 } : img)),
    );
    try {
      const result = await likeImage(filename);
      setImages((current) =>
        current.map((img) => (img.filename === filename ? { ...img, likes: result.likes } : img)),
      );
    } catch {
      setImages(previous);
    }
  }

  async function handleRetry(filename) {
    const updated = await retryBlur(filename);
    setImages((current) => current.map((img) => (img.filename === filename ? updated : img)));
    return updated;
  }

  if (loading) {
    return (
      <div className="max-w-6xl mx-auto px-4 sm:px-6 py-8">
        <SkeletonGrid />
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex flex-col items-center text-center py-24 px-4">
        <Icon name="error" className="text-[40px] text-[#e60023] mb-3" />
        <p className="text-gray-700 font-medium">{error}</p>
      </div>
    );
  }

  if (images.length === 0) {
    return <EmptyState />;
  }

  return (
    <div className="max-w-6xl mx-auto px-4 sm:px-6 py-8">
      <Masonry breakpointCols={breakpoints} className="flex gap-4" columnClassName="flex flex-col gap-4">
        {images.map((image) => (
          <ImageCard key={image.key} image={image} onLike={handleLike} onRetry={handleRetry} />
        ))}
      </Masonry>
    </div>
  );
}
