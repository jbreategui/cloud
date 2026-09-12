import { Link, useLocation } from 'react-router-dom';
import Icon from './Icon';

export default function Fab() {
  const location = useLocation();
  if (location.pathname === '/upload') return null;

  return (
    <Link
      to="/upload"
      aria-label="Subir imagen"
      className="fixed bottom-6 right-6 z-20 w-14 h-14 rounded-full bg-[#e60023] text-white shadow-lg shadow-red-900/20 flex items-center justify-center hover:bg-[#ad081b] hover:scale-105 active:scale-95 transition-all"
    >
      <Icon name="add" className="text-[28px]" />
    </Link>
  );
}
