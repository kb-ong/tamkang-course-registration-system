import { useCallback, useEffect, useRef, useState } from 'react';
import type { HTMLAttributes } from 'react';
import { Alert, Button, Card, Empty, Spin, Table } from 'antd';
import type { TableColumnsType } from 'antd';
import {
  DownloadOutlined,
  PrinterOutlined,
  ProfileOutlined,
} from '@ant-design/icons';
import { ApiError } from '../api/client';
import { dropCourse, fetchRegistrations } from '../api/registrations';
import type { RegisteredCourse, RegistrationSchedule, SemesterOption } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { downloadTextFile } from '../utils/download';
import { buildScheduleCsv, scheduleCsvFileName } from '../utils/scheduleCsv';

type Feedback = { kind: 'success' | 'error'; text: string } | null;

const GENERIC_DROP_ERROR = 'Unable to drop the course. Please try again later.';

/** "2026-09-01T09:00:00" -> "2026-09-01 09:00" */
function formatDateTime(value: string): string {
  return value.replace('T', ' ').slice(0, 16);
}

export function RegisteredCoursesSection() {
  const { user, markAnonymous } = useAuth();

  const [data, setData] = useState<RegistrationSchedule | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [selected, setSelected] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<Feedback>(null);
  const [confirming, setConfirming] = useState<number | null>(null);
  const [pending, setPending] = useState<number | null>(null);

  const mounted = useRef(true);
  const requestSeq = useRef(0);
  const selectedRef = useRef<string | null>(null);
  const pendingRef = useRef<number | null>(null);

  const load = useCallback(
    async (code?: string) => {
      const seq = ++requestSeq.current;
      setLoading(true);
      setLoadError(false);
      try {
        const result = code ? await fetchRegistrations(code) : await fetchRegistrations();
        if (!mounted.current || seq !== requestSeq.current) return;
        setData(result);
        const next = result.semester?.code ?? null;
        selectedRef.current = next;
        setSelected(next);
        setLoading(false);
      } catch (err) {
        if (!mounted.current || seq !== requestSeq.current) return;
        if (err instanceof ApiError && err.status === 401) {
          markAnonymous();
          return;
        }
        setLoadError(true);
        setLoading(false);
      }
    },
    [markAnonymous],
  );

  useEffect(() => {
    mounted.current = true;
    void load();
    return () => {
      mounted.current = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleSemesterChange = (code: string) => {
    if (!code) return;
    setFeedback(null);
    setConfirming(null);
    selectedRef.current = code;
    setSelected(code);
    void load(code);
  };

  const refetch = () => {
    const code = selectedRef.current;
    void (code ? load(code) : load());
  };

  const handleDropClick = (courseId: number) => {
    setFeedback(null);
    setConfirming(courseId);
  };

  const handleConfirm = async (course: RegisteredCourse) => {
    if (pendingRef.current !== null) return;
    pendingRef.current = course.courseId;
    setPending(course.courseId);
    try {
      const result = await dropCourse(course.courseId);
      if (!mounted.current) return;
      setFeedback({ kind: 'success', text: result.message });
      setConfirming(null);
      refetch();
    } catch (err) {
      if (!mounted.current) return;
      if (err instanceof ApiError && err.status === 401) {
        markAnonymous();
        return;
      }
      setConfirming(null);
      if (err instanceof ApiError && err.status >= 400 && err.status < 500) {
        setFeedback({ kind: 'error', text: err.message });
        refetch();
      } else {
        setFeedback({ kind: 'error', text: GENERIC_DROP_ERROR });
      }
    } finally {
      pendingRef.current = null;
      if (mounted.current) setPending(null);
    }
  };

  const semester = data?.semester ?? null;
  const courses = data?.courses ?? [];
  const totalCredits = data?.totalCredits ?? 0;
  const period = data?.registrationPeriod ?? null;
  const open = period?.open === true;
  const hasRows = semester !== null && courses.length > 0;

  const options: SemesterOption[] = [...(data?.semesters ?? [])];
  if (semester && !options.some((s) => s.code === semester.code)) {
    options.unshift(semester);
  }

  const columns: TableColumnsType<RegisteredCourse> = [
    {
      title: 'Code',
      key: 'code',
      width: 110,
      render: (_, c) => (
        <span className="course-code" data-testid={`registered-code-${c.courseId}`}>
          {c.code}
        </span>
      ),
    },
    {
      title: 'Name',
      key: 'name',
      render: (_, c) => (
        <span className="course-name" data-testid={`registered-name-${c.courseId}`}>
          {c.name}
        </span>
      ),
    },
    {
      title: 'Credits',
      key: 'credits',
      width: 90,
      align: 'center',
      render: (_, c) => <span data-testid={`registered-credits-${c.courseId}`}>{c.credits}</span>,
    },
    {
      title: 'Instructor',
      key: 'instructor',
      render: (_, c) => (
        <span data-testid={`registered-instructor-${c.courseId}`}>{c.instructor}</span>
      ),
    },
    {
      title: 'Registered On',
      key: 'registeredAt',
      width: 160,
      render: (_, c) => (
        <span data-testid={`registered-date-${c.courseId}`}>{formatDateTime(c.registeredAt)}</span>
      ),
    },
    {
      title: 'Action',
      key: 'action',
      width: 250,
      className: 'no-print',
      render: (_, c) =>
        confirming === c.courseId ? (
          <div className="drop-confirm">
            <span className="drop-confirm__text" data-testid={`drop-confirm-text-${c.courseId}`}>
              {`Drop ${c.code}?`}
            </span>
            <Button
              danger
              type="primary"
              size="small"
              data-testid={`drop-confirm-${c.courseId}`}
              loading={pending === c.courseId}
              onClick={() => void handleConfirm(c)}
            >
              Confirm drop
            </Button>
            <Button
              size="small"
              data-testid={`drop-cancel-${c.courseId}`}
              onClick={() => setConfirming(null)}
            >
              Cancel
            </Button>
          </div>
        ) : (
          <Button
            danger
            data-testid={`drop-btn-${c.courseId}`}
            disabled={!open}
            onClick={() => handleDropClick(c.courseId)}
          >
            Drop
          </Button>
        ),
    },
  ];

  const renderBody = () => {
    if (loadError) {
      return (
        <Alert
          className="no-print"
          data-testid="registered-error"
          type="error"
          showIcon
          message="Unable to load your registered courses. Please try again later."
        />
      );
    }
    if (!data) {
      return (
        <div className="registered-loading no-print" data-testid="registered-loading">
          <Spin />
        </div>
      );
    }
    if (!hasRows) {
      const text = semester
        ? 'You have not registered for any courses in this semester.'
        : 'No semester has been selected.';
      return (
        <Empty
          className="registered-empty"
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description={<span data-testid="registered-empty">{text}</span>}
        />
      );
    }
    return (
      <div data-testid="registered-table" className="courses-table registered-table">
        <Table<RegisteredCourse>
          rowKey="courseId"
          columns={columns}
          dataSource={courses}
          pagination={false}
          loading={loading}
          scroll={{ x: 720 }}
          onRow={(c) =>
            ({ 'data-testid': `registered-row-${c.courseId}` }) as HTMLAttributes<HTMLElement>
          }
        />
      </div>
    );
  };

  return (
    <Card
      data-testid="registered-card"
      className="dash-card registered-card print-area"
      variant="borderless"
      title={
        <span className="dash-card__title">
          <span className="dash-card__title-icon" aria-hidden="true">
            <ProfileOutlined />
          </span>
          My registered courses
        </span>
      }
    >
      {data && (
        <div className="print-header print-only" data-testid="print-header">
          <div className="print-header__student">{`${user?.name ?? ''} (${user?.studentId ?? ''})`}</div>
          <div className="print-header__semester">
            {semester ? `${semester.name} (${semester.code})` : ''}
          </div>
          <div className="print-header__total">{`Total credit hours: ${totalCredits}`}</div>
        </div>
      )}

      {feedback && (
        <Alert
          className="registered-feedback no-print"
          data-testid={feedback.kind === 'success' ? 'drop-success' : 'drop-error'}
          type={feedback.kind}
          showIcon
          message={feedback.text}
        />
      )}

      {data && period && !open && (
        <Alert
          className="registered-note no-print"
          data-testid="drop-closed-note"
          type="warning"
          showIcon
          message="Dropping courses is only possible during the registration period."
        />
      )}

      <div className="registered-toolbar no-print">
        <div className="courses-toolbar__field">
          <label htmlFor="registered-semester-select" className="courses-toolbar__label">
            Semester
          </label>
          <select
            id="registered-semester-select"
            data-testid="registered-semester-select"
            className="semester-select"
            value={selected ?? ''}
            disabled={!data}
            onChange={(e) => handleSemesterChange(e.target.value)}
          >
            {!semester && <option value="">Select a semester</option>}
            {options.map((s) => (
              <option key={s.code} value={s.code}>
                {`${s.name} (${s.code})`}
              </option>
            ))}
          </select>
        </div>
        <div className="registered-toolbar__actions">
          <Button
            icon={<PrinterOutlined />}
            data-testid="print-btn"
            disabled={!hasRows}
            onClick={() => window.print()}
          >
            Print
          </Button>
          <Button
            icon={<DownloadOutlined />}
            data-testid="csv-btn"
            disabled={!hasRows}
            onClick={() =>
              semester &&
              downloadTextFile(
                scheduleCsvFileName(semester.code),
                buildScheduleCsv(courses, totalCredits),
              )
            }
          >
            Download CSV
          </Button>
        </div>
      </div>

      {renderBody()}

      {data && (
        <div className="registered-total">
          <span className="registered-total__label">Total credit hours:</span>
          <span className="registered-total__value" data-testid="total-credits">
            {totalCredits}
          </span>
        </div>
      )}
    </Card>
  );
}
