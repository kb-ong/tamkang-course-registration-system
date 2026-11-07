import { useEffect, useRef, useState } from 'react';
import { Alert, Button, Card, Form, Input, Typography } from 'antd';
import { LockOutlined, LoginOutlined, UserOutlined } from '@ant-design/icons';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { BrandMark } from '../components/BrandMark';
import { FullPageLoading } from '../components/FullPageLoading';

const APP_NAME = 'Tamkang Course Registration System';
const MSG_INVALID = 'Invalid student ID or password.';
const MSG_GENERIC = 'Unable to sign in. Please try again later.';

interface LoginFormValues {
  studentId: string;
  password: string;
}

function loginErrorMessage(err: unknown): string {
  if (err instanceof ApiError && err.status === 401) {
    return err.message || MSG_INVALID;
  }
  return MSG_GENERIC;
}

export function LoginPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const mounted = useRef(true);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  const loggedOut =
    (location.state as { loggedOut?: unknown } | null)?.loggedOut === true;

  if (auth.status === 'loading') {
    return <FullPageLoading />;
  }
  if (auth.status === 'authenticated') {
    return <Navigate to="/dashboard" replace />;
  }

  const onFinish = async (values: LoginFormValues) => {
    setError(null);
    setSubmitting(true);
    try {
      await auth.login(values.studentId, values.password);
      navigate('/dashboard', { replace: true });
    } catch (err) {
      if (mounted.current) {
        setError(loginErrorMessage(err));
      }
    } finally {
      if (mounted.current) {
        setSubmitting(false);
      }
    }
  };

  return (
    <div className="login-page">
      <div className="login-page__decor login-page__decor--one" aria-hidden="true" />
      <div className="login-page__decor login-page__decor--two" aria-hidden="true" />

      <main className="login-page__inner">
        <div className="login-brand-block">
          <BrandMark size={64} />
          <Typography.Title level={1} className="login-brand-block__title" data-testid="login-brand">
            {APP_NAME}
          </Typography.Title>
          <Typography.Text className="login-brand-block__tagline">
            Online Course Registration Portal
          </Typography.Text>
        </div>

        <Card
          data-testid="login-card"
          className="login-card"
          variant="borderless"
          title={
            <div className="login-card__heading">
              <div className="login-card__title">Student Login</div>
              <div className="login-card__subtitle">Sign in with your student ID and password</div>
            </div>
          }
        >
          {loggedOut && !error && (
            <Alert
              data-testid="logout-message"
              type="success"
              showIcon
              message="You have been logged out."
              className="login-card__alert"
            />
          )}
          {error && (
            <Alert
              data-testid="login-error"
              type="error"
              showIcon
              message={error}
              className="login-card__alert"
            />
          )}

          <Form<LoginFormValues>
            layout="vertical"
            requiredMark={false}
            size="large"
            onFinish={onFinish}
            disabled={submitting}
          >
            <Form.Item
              label="Student ID"
              name="studentId"
              rules={[{ required: true, whitespace: true, message: 'Please enter your student ID.' }]}
            >
              <Input
                data-testid="login-student-id"
                prefix={<UserOutlined className="login-card__input-icon" />}
                placeholder="e.g. 410000001"
                autoComplete="username"
                inputMode="numeric"
                autoFocus
              />
            </Form.Item>
            <Form.Item
              label="Password"
              name="password"
              rules={[{ required: true, message: 'Please enter your password.' }]}
            >
              <Input.Password
                data-testid="login-password"
                prefix={<LockOutlined className="login-card__input-icon" />}
                autoComplete="current-password"
              />
            </Form.Item>
            <Form.Item className="login-card__submit-item">
              <Button
                data-testid="login-submit"
                type="primary"
                htmlType="submit"
                block
                loading={submitting}
                icon={<LoginOutlined aria-hidden="true" />}
              >
                Log in
              </Button>
            </Form.Item>
          </Form>
        </Card>
      </main>
    </div>
  );
}
