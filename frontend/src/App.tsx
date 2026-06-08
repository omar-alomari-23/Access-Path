import './App.css';
import Navbar from './components/layout/Navbar';
import AppRouter from './router/AppRouter';

export default function App() {
  return (
    <div className="app">
      <Navbar />
      <AppRouter />
    </div>
  );
}
