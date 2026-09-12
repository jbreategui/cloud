import { Route, Routes } from 'react-router-dom';
import Fab from './components/Fab';
import NavBar from './components/NavBar';
import GalleryPage from './pages/GalleryPage';
import UploadPage from './pages/UploadPage';

export default function App() {
  return (
    <div className="min-h-screen">
      <NavBar />
      <Routes>
        <Route path="/" element={<GalleryPage />} />
        <Route path="/upload" element={<UploadPage />} />
      </Routes>
      <Fab />
    </div>
  );
}
