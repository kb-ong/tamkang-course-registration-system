import { useState } from 'react';
import { Avatar, Button, Grid, Layout, Menu } from 'antd';
import { DashboardOutlined, LogoutOutlined } from '@ant-design/icons';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { BrandMark } from './BrandMark';

const { Header, Sider, Content, Footer } = Layout;

const NAV_ITEMS = [
  {
    key: '/dashboard',
    icon: <DashboardOutlined aria-hidden="true" />,
    label: 'Dashboard',
  },
];

export function AppLayout() {
  const auth = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const screens = Grid.useBreakpoint();
  const [collapsed, setCollapsed] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);

  const name = auth.user?.name ?? '';
  const initial = name.trim().charAt(0).toUpperCase() || '?';
  const isPhone = screens.sm === false;

  const selectedKeys = NAV_ITEMS.filter((item) => location.pathname.startsWith(item.key)).map(
    (item) => item.key,
  );

  const handleLogout = async () => {
    setLoggingOut(true);
    await auth.logout();
    navigate('/login', { replace: true, state: { loggedOut: true } });
  };

  return (
    <Layout className="app-shell">
      <Header className="app-header">
        <div className="app-header__brand">
          <BrandMark size={36} />
          <span className="app-header__title" data-testid="app-title">
            Tamkang Course Registration System
          </span>
        </div>
        <div className="app-header__user" data-testid="user-menu">
          <Avatar className="app-header__avatar" size={34}>
            {initial}
          </Avatar>
          <span className="app-header__user-name" data-testid="user-name">
            {name}
          </span>
          <Button
            data-testid="logout-button"
            className="app-header__logout"
            ghost
            icon={<LogoutOutlined aria-hidden="true" />}
            loading={loggingOut}
            onClick={handleLogout}
          >
            Log out
          </Button>
        </div>
      </Header>

      <Layout className="app-body">
        <Sider
          className="app-sider"
          theme="light"
          width={220}
          breakpoint="lg"
          collapsible
          collapsed={collapsed}
          onCollapse={(value) => setCollapsed(value)}
          collapsedWidth={isPhone ? 0 : 64}
        >
          <div data-testid="side-nav" className="app-sider__nav">
            <Menu
              mode="inline"
              selectedKeys={selectedKeys}
              items={NAV_ITEMS}
              onClick={({ key }) => {
                navigate(key);
                if (isPhone) setCollapsed(true);
              }}
            />
          </div>
        </Sider>

        <Layout className="app-main">
          <Content className="app-content">
            <div className="app-content__inner">
              <Outlet />
            </div>
          </Content>
          <Footer className="app-footer">Tamkang Course Registration System · Phase 1</Footer>
        </Layout>
      </Layout>
    </Layout>
  );
}
