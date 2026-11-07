import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Avatar, Card, Col, Descriptions, Empty, Row, Spin, Tag, Typography } from 'antd';
import {
  CalendarOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  IdcardOutlined,
} from '@ant-design/icons';
import { ApiError } from '../api/client';
import { fetchDashboard } from '../api/dashboard';
import type { CurrentSemester, DashboardData } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { RegisteredCoursesSection } from '../components/RegisteredCoursesSection';

type LoadState =
  | { kind: 'loading' }
  | { kind: 'loaded'; data: DashboardData }
  | { kind: 'error' };

/** "2026-08-01T00:00:00" → "2026-08-01 00:00" */
function formatDateTime(value: string): string {
  return value.replace('T', ' ').slice(0, 16);
}

function formatPeriod(semester: CurrentSemester): string {
  if (!semester.registrationStartAt || !semester.registrationEndAt) {
    return 'Not scheduled';
  }
  return `${formatDateTime(semester.registrationStartAt)} to ${formatDateTime(semester.registrationEndAt)}`;
}

function CardTitle({ icon, text }: { icon: ReactNode; text: string }) {
  return (
    <span className="dash-card__title">
      <span className="dash-card__title-icon" aria-hidden="true">
        {icon}
      </span>
      {text}
    </span>
  );
}

function ProfileCard({ data }: { data: DashboardData }) {
  const { student } = data;
  return (
    <Card
      data-testid="profile-card"
      className="dash-card"
      variant="borderless"
      title={<CardTitle icon={<IdcardOutlined />} text="Student Profile" />}
    >
      <div className="profile-summary">
        <Avatar size={56} className="profile-summary__avatar">
          {student.name.trim().charAt(0).toUpperCase()}
        </Avatar>
        <div className="profile-summary__text">
          <div className="profile-summary__name">{student.name}</div>
          <div className="profile-summary__role">Student</div>
        </div>
      </div>
      <Descriptions column={1} size="small" colon={false} className="dash-descriptions">
        <Descriptions.Item label="Student ID">
          <span data-testid="profile-student-id">{student.studentId}</span>
        </Descriptions.Item>
        <Descriptions.Item label="Name">
          <span data-testid="profile-name">{student.name}</span>
        </Descriptions.Item>
        <Descriptions.Item label="Email">
          <span data-testid="profile-email">{student.email}</span>
        </Descriptions.Item>
      </Descriptions>
    </Card>
  );
}

function SemesterCard({ semester }: { semester: CurrentSemester | null }) {
  return (
    <Card
      data-testid="semester-card"
      className="dash-card"
      variant="borderless"
      title={<CardTitle icon={<CalendarOutlined />} text="Current Semester" />}
    >
      {semester ? (
        <Descriptions column={1} size="small" colon={false} className="dash-descriptions">
          <Descriptions.Item label="Semester">
            <span data-testid="semester-name">{`${semester.name} (${semester.code})`}</span>
          </Descriptions.Item>
          <Descriptions.Item label="Registration Period">
            <span data-testid="registration-period">{formatPeriod(semester)}</span>
          </Descriptions.Item>
          <Descriptions.Item label="Status">
            {semester.registrationOpen ? (
              <Tag
                data-testid="registration-status"
                color="success"
                icon={<CheckCircleOutlined aria-hidden="true" />}
              >
                Open
              </Tag>
            ) : (
              <Tag
                data-testid="registration-status"
                color="error"
                icon={<ClockCircleOutlined aria-hidden="true" />}
              >
                Closed
              </Tag>
            )}
          </Descriptions.Item>
        </Descriptions>
      ) : (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description={<span data-testid="semester-empty">No current semester has been set.</span>}
        />
      )}
    </Card>
  );
}

export function DashboardPage() {
  const { user, markAnonymous } = useAuth();
  const [state, setState] = useState<LoadState>({ kind: 'loading' });

  useEffect(() => {
    let active = true;
    (async () => {
      try {
        const data = await fetchDashboard();
        if (active) setState({ kind: 'loaded', data });
      } catch (err) {
        if (!active) return;
        if (err instanceof ApiError && err.status === 401) {
          markAnonymous();
          return;
        }
        setState({ kind: 'error' });
      }
    })();
    return () => {
      active = false;
    };
  }, [markAnonymous]);

  return (
    <div className="dashboard">
      <section className="welcome-banner">
        <Typography.Title level={2} className="welcome-banner__title" data-testid="welcome-message">
          {`Welcome, ${user?.name ?? ''}`}
        </Typography.Title>
        <Typography.Paragraph className="welcome-banner__subtitle">
          Here is an overview of your student account.
        </Typography.Paragraph>
      </section>

      {state.kind === 'loading' && (
        <div data-testid="dashboard-loading" className="dashboard__loading">
          <Spin size="large" />
        </div>
      )}

      {state.kind === 'error' && (
        <Alert
          data-testid="dashboard-error"
          type="error"
          showIcon
          message="Unable to load your dashboard. Please try again later."
        />
      )}

      {state.kind === 'loaded' && (
        <Row gutter={[24, 24]} className="dashboard__cards">
          <Col xs={24} lg={12}>
            <ProfileCard data={state.data} />
          </Col>
          <Col xs={24} lg={12}>
            <SemesterCard semester={state.data.currentSemester ?? null} />
          </Col>
          <Col xs={24}>
            <RegisteredCoursesSection />
          </Col>
        </Row>
      )}
    </div>
  );
}
