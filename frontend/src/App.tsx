import { Navigate, Route, Routes } from 'react-router-dom';
import { ProtectedRoute } from './auth/ProtectedRoute';
import { AppLayout } from './layout/AppLayout';
import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage';
import { PatioPage } from './pages/PatioPage';
import { RelatoriosPage } from './pages/RelatoriosPage';
import { CrudResourcePage } from './components/crud/CrudResourcePage';
import { allConfigs } from './resources';

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        path="/app"
        element={
          <ProtectedRoute>
            <AppLayout />
          </ProtectedRoute>
        }
      >
        <Route index element={<DashboardPage />} />
        <Route path="patio" element={<PatioPage />} />
        <Route path="relatorios" element={<RelatoriosPage />} />
        {allConfigs.map((config) => (
          <Route key={config.key} path={config.key} element={<CrudResourcePage config={config} />} />
        ))}
      </Route>
      <Route path="/" element={<Navigate to="/app" replace />} />
      <Route path="*" element={<Navigate to="/app" replace />} />
    </Routes>
  );
}
