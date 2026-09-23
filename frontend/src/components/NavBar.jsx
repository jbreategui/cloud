import { useEffect, useState } from 'react';
import { NavLink } from 'react-router-dom';
import { getServerInfo } from '../api/client';
import Icon from './Icon';

const linkClasses = ({ isActive }) =>
  `flex items-center gap-1.5 px-4 py-2 rounded-full text-sm font-semibold transition-colors ${
    isActive ? 'bg-[#1a1a1a] text-white' : 'text-gray-600 hover:bg-gray-200/70'
  }`;

function ServerBadge() {
  const [serverInfo, setServerInfo] = useState(null);

  useEffect(() => {
    getServerInfo()
      .then(setServerInfo)
      .catch(() => setServerInfo(null));
  }, []);

  if (!serverInfo) {
    return null;
  }

  return (
    <span className="hidden md:inline text-xs font-medium text-gray-500 shrink-0">
      Servidor: {serverInfo.server} · IP: {serverInfo.ip}
    </span>
  );
}

export default function NavBar() {
  return (
    <header className="sticky top-0 z-20 bg-white/85 backdrop-blur-md border-b border-gray-200/80">
      <div className="max-w-6xl mx-auto flex items-center justify-between gap-4 px-4 sm:px-6 py-3">
        <NavLink to="/" className="flex items-center gap-2 shrink-0">
          <span className="w-9 h-9 rounded-full bg-[#e60023] flex items-center justify-center">
            <Icon name="image" className="text-white" />
          </span>
          <span className="text-lg font-extrabold tracking-tight hidden sm:inline">PixelBoard</span>
        </NavLink>

        <nav className="flex items-center gap-1.5 bg-gray-100 rounded-full p-1">
          <NavLink to="/" end className={linkClasses}>
            <Icon name="grid_view" className="text-[20px]" />
            <span className="hidden sm:inline">Explorar</span>
          </NavLink>
          <NavLink to="/upload" className={linkClasses}>
            <Icon name="add_circle" className="text-[20px]" />
            <span className="hidden sm:inline">Crear</span>
          </NavLink>
        </nav>

        <ServerBadge />
      </div>
    </header>
  );
}
