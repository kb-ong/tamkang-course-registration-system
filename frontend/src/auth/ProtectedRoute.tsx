import { Navigate, Outlet } from 'react-router-dom';
import { FullPageLoading } from '../components/FullPageLoading';
import { useAuth } from './AuthContext';

export function ProtectedRoute() {
  const { status } = useAuth();
  if (status === 'loading') {
    return <FullPageLoading />;
  }
  if (status === 'anonymous') {
    return <Navigate to="/login" replace />;
  }
  return <Outlet />;
}
