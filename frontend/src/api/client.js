import axios from 'axios';

export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? 'http://localhost:8080',
});

export function uploadImage(file) {
  const formData = new FormData();
  formData.append('file', file);
  return apiClient.post('/api/images', formData).then((res) => res.data);
}

export function listImages() {
  return apiClient.get('/api/images').then((res) => res.data);
}

export function likeImage(filename) {
  return apiClient.post(`/api/images/${filename}/like`).then((res) => res.data);
}
