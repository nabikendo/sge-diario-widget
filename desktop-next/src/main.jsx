import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import LimiteDeErro from './components/LimiteDeErro';
import './styles.css';

createRoot(document.getElementById('root')).render(<StrictMode><LimiteDeErro><App /></LimiteDeErro></StrictMode>);
