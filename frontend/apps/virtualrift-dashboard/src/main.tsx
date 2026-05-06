import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import './app.css';
import { SessionProvider } from './session';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <SessionProvider>
      <App />
    </SessionProvider>
  </StrictMode>,
);
