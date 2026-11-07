import { App as AntApp, ConfigProvider } from 'antd';
import type { ThemeConfig } from 'antd';
import { Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './auth/AuthContext';
import { ProtectedRoute } from './auth/ProtectedRoute';
import { AppLayout } from './components/AppLayout';
import { CoursesPage } from './pages/CoursesPage';
import { DashboardPage } from './pages/DashboardPage';
import { LoginPage } from './pages/LoginPage';

const NAVY = '#1f3a68';
const NAVY_DARK = '#142849';

const theme: ThemeConfig = {
  token: {
    colorPrimary: NAVY,
    colorInfo: NAVY,
    colorLink: NAVY,
    colorBgLayout: '#f3f5f9',
    borderRadius: 8,
    fontFamily:
      "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, 'Noto Sans', sans-serif",
  },
  components: {
    Layout: {
      headerBg: NAVY_DARK,
      headerHeight: 64,
      headerPadding: '0 24px',
      siderBg: '#ffffff',
      triggerBg: '#eef1f7',
      triggerColor: NAVY,
      footerBg: 'transparent',
      bodyBg: '#f3f5f9',
    },
    Menu: {
      itemSelectedBg: '#e8eef8',
      itemSelectedColor: NAVY,
      itemBorderRadius: 8,
      itemMarginInline: 8,
    },
    Card: {
      headerFontSize: 16,
    },
  },
};

export default function App() {
  return (
    <ConfigProvider theme={theme}>
      <AntApp>
        <AuthProvider>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route element={<ProtectedRoute />}>
              <Route element={<AppLayout />}>
                <Route path="/dashboard" element={<DashboardPage />} />
                <Route path="/courses" element={<CoursesPage />} />
              </Route>
            </Route>
            <Route path="/" element={<Navigate to="/dashboard" replace />} />
            <Route path="*" element={<Navigate to="/dashboard" replace />} />
          </Routes>
        </AuthProvider>
      </AntApp>
    </ConfigProvider>
  );
}
