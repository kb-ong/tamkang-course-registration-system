import { useCallback, useEffect, useRef, useState } from 'react';
import type { HTMLAttributes } from 'react';
import { Alert, Button, Card, Empty, Input, Progress, Spin, Table, Tag, Typography } from 'antd';
import type { TableColumnsType } from 'antd';
import { BookOutlined, CheckCircleFilled, SearchOutlined } from '@ant-design/icons';
import { ApiError } from '../api/client';
import { fetchCourses, fetchSemesters, registerForCourse } from '../api/courses';
import type { CourseListing, CourseSummary, SemesterOption } from '../api/types';
import { useAuth } from '../auth/AuthContext';

type Feedback = { kind: 'success' | 'error'; text: string } | null;

/** "2026-08-01T00:00:00" -> "2026-08-01 00:00" */
function formatDateTime(value: string): string {
  return value.replace('T', ' ').slice(0, 16);
}

const GENERIC_REGISTER_ERROR = 'Unable to register. Please try again later.';

function seatColor(course: CourseSummary): string {
  if (course.availableSeats <= 0) return 'red';
  if (course.capacity > 0 && course.availableSeats / course.capacity <= 0.2) return 'orange';
  return 'green';
}

function seatPercent(course: CourseSummary): number {
  if (course.capacity <= 0) return 0;
  return Math.max(0, Math.min(100, Math.round((course.availableSeats / course.capacity) * 100)));
}

function seatStroke(color: string): string {
  if (color === 'red') return '#cf1322';
  if (color === 'orange') return '#d48806';
  return '#389e0d';
}

export function CoursesPage() {
  const auth = useAuth();
  const markAnonymous = auth.markAnonymous;

  const [semesters, setSemesters] = useState<SemesterOption[]>([]);
  const [listing, setListing] = useState<CourseListing | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [draft, setDraft] = useState('');
  const [appliedQuery, setAppliedQuery] = useState('');
  const [pending, setPending] = useState<ReadonlySet<number>>(new Set());
  const [feedback, setFeedback] = useState<Feedback>(null);

  const mounted = useRef(true);
  const requestSeq = useRef(0);

  const semesterCode = listing?.semester?.code ?? '';
  const period = listing?.registrationPeriod ?? null;
  const open = period ? period.open : true;

  const load = useCallback(
    async (semester: string, q: string) => {
      const seq = ++requestSeq.current;
      setLoading(true);
      setLoadError(false);
      const params: { semester?: string; q?: string } = {};
      if (semester) params.semester = semester;
      if (q) params.q = q;
      try {
        const result = await fetchCourses(params);
        if (!mounted.current || seq !== requestSeq.current) return;
        setListing(result);
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
    void load('', '');
    fetchSemesters().then(
      (list) => {
        if (mounted.current) setSemesters(list);
      },
      () => {
        /* the select falls back to the listing's semester */
      },
    );
    return () => {
      mounted.current = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const options: SemesterOption[] = [...semesters];
  if (listing?.semester && !options.some((s) => s.code === listing.semester!.code)) {
    options.unshift(listing.semester);
  }

  const handleSemesterChange = (value: string) => {
    if (!value) return;
    setFeedback(null);
    void load(value, appliedQuery);
  };

  const handleSearch = () => {
    const q = draft.trim();
    setFeedback(null);
    setAppliedQuery(q);
    void load(semesterCode, q);
  };

  const handleRegister = async (course: CourseSummary) => {
    if (pending.has(course.id)) return;
    setFeedback(null);
    setPending((prev) => new Set(prev).add(course.id));
    try {
      const result = await registerForCourse(course.id);
      if (!mounted.current) return;
      setListing((prev) =>
        prev
          ? {
              ...prev,
              courses: prev.courses.map((c) =>
                c.id === course.id
                  ? { ...c, availableSeats: result.availableSeats, registered: true }
                  : c,
              ),
            }
          : prev,
      );
      setFeedback({ kind: 'success', text: result.message });
    } catch (err) {
      if (!mounted.current) return;
      if (err instanceof ApiError && err.status === 401) {
        markAnonymous();
        return;
      }
      if (err instanceof ApiError && err.status >= 400 && err.status < 500) {
        setFeedback({ kind: 'error', text: err.message });
        void load(semesterCode, appliedQuery);
      } else {
        setFeedback({ kind: 'error', text: GENERIC_REGISTER_ERROR });
      }
    } finally {
      if (mounted.current) {
        setPending((prev) => {
          const next = new Set(prev);
          next.delete(course.id);
          return next;
        });
      }
    }
  };

  const columns: TableColumnsType<CourseSummary> = [
    {
      title: 'Code',
      key: 'code',
      width: 110,
      render: (_, c) => (
        <span className="course-code" data-testid={`course-code-${c.id}`}>
          {c.code}
        </span>
      ),
    },
    {
      title: 'Name',
      key: 'name',
      render: (_, c) => (
        <span className="course-name" data-testid={`course-name-${c.id}`}>
          {c.name}
        </span>
      ),
    },
    {
      title: 'Credits',
      key: 'credits',
      width: 100,
      align: 'center',
      render: (_, c) => (
        <span className="course-credits" data-testid={`course-credits-${c.id}`}>
          {c.credits}
        </span>
      ),
    },
    {
      title: 'Instructor',
      key: 'instructor',
      render: (_, c) => <span data-testid={`course-instructor-${c.id}`}>{c.instructor}</span>,
    },
    {
      title: 'Available Seats',
      key: 'seats',
      width: 190,
      render: (_, c) => {
        const color = seatColor(c);
        return (
          <div className="seat-cell">
            <div className="seat-cell__top">
              <Tag color={color} className="seat-cell__tag">
                <span data-testid={`course-seats-${c.id}`}>{c.availableSeats}</span>
              </Tag>
              {c.availableSeats <= 0 && (
                <Tag color="red" data-testid={`full-tag-${c.id}`}>
                  Full
                </Tag>
              )}
              <span className="seat-cell__capacity">of {c.capacity}</span>
            </div>
            <Progress
              percent={seatPercent(c)}
              showInfo={false}
              size="small"
              strokeColor={seatStroke(color)}
              aria-hidden="true"
            />
          </div>
        );
      },
    },
    {
      title: 'Action',
      key: 'action',
      width: 200,
      render: (_, c) => (
        <div className="action-cell">
          <Button
            type="primary"
            data-testid={`register-btn-${c.id}`}
            disabled={c.registered || pending.has(c.id) || c.availableSeats <= 0 || !open}
            loading={pending.has(c.id)}
            onClick={() => void handleRegister(c)}
          >
            Register
          </Button>
          {!c.registered && c.availableSeats <= 0 && (
            <span className="full-note" data-testid={`full-note-${c.id}`}>
              No seats available.
            </span>
          )}
          {!c.registered && !open && (
            <span className="closed-note" data-testid={`closed-note-${c.id}`}>
              Registration is closed.
            </span>
          )}
          {c.registered && (
            <Tag
              color="green"
              icon={<CheckCircleFilled />}
              className="registered-tag"
              data-testid={`registered-tag-${c.id}`}
            >
              Registered
            </Tag>
          )}
        </div>
      ),
    },
  ];

  const renderBanner = () => {
    if (!listing?.semester || !period) return null;
    const hasDates = period.startAt && period.endAt;
    const range = hasDates
      ? `Period: ${formatDateTime(period.startAt!)} to ${formatDateTime(period.endAt!)}.`
      : '';
    let text: string;
    if (!hasDates) {
      text = 'Registration is closed. No registration period is scheduled for this semester.';
    } else {
      text = `Registration is ${period.open ? 'open' : 'closed'}. ${range}`;
    }
    return (
      <Alert
        className="period-banner"
        data-testid="period-banner"
        data-status={period.open ? 'open' : 'closed'}
        type={period.open ? 'success' : 'warning'}
        showIcon
        message={text}
      />
    );
  };

  const renderBody = () => {
    if (loadError) {
      return (
        <Alert
          data-testid="courses-error"
          type="error"
          showIcon
          message="Unable to load courses. Please try again later."
        />
      );
    }
    if (!listing) {
      return (
        <div className="courses-loading" data-testid="courses-loading">
          <Spin size="large" />
        </div>
      );
    }
    if (listing.courses.length === 0) {
      const text = listing.semester ? 'No courses found.' : 'No semester has been selected.';
      return (
        <Empty
          className="courses-empty"
          description={<span data-testid="courses-empty">{text}</span>}
        />
      );
    }
    return (
      <div data-testid="courses-table" className="courses-table">
        <Table<CourseSummary>
          rowKey="id"
          columns={columns}
          dataSource={listing.courses}
          pagination={false}
          loading={loading}
          scroll={{ x: 760 }}
          onRow={(c) =>
            ({ 'data-testid': `course-row-${c.id}` }) as HTMLAttributes<HTMLElement>
          }
        />
      </div>
    );
  };

  return (
    <div className="courses-page">
      <div className="page-header">
        <span className="page-header__icon" aria-hidden="true">
          <BookOutlined />
        </span>
        <div>
          <Typography.Title level={2} className="page-header__title" data-testid="courses-title">
            Available Courses
          </Typography.Title>
          <Typography.Text className="page-header__subtitle">
            Browse the course offering and register in one click.
          </Typography.Text>
        </div>
      </div>

      {feedback && (
        <Alert
          className="courses-feedback"
          data-testid={feedback.kind === 'success' ? 'register-success' : 'register-error'}
          type={feedback.kind}
          showIcon
          message={feedback.text}
        />
      )}

      {renderBanner()}

      <Card className="courses-card" variant="borderless">
        <div className="courses-toolbar">
          <div className="courses-toolbar__field">
            <label htmlFor="semester-select" className="courses-toolbar__label">
              Semester
            </label>
            <select
              id="semester-select"
              data-testid="semester-select"
              className="semester-select"
              value={semesterCode}
              onChange={(e) => handleSemesterChange(e.target.value)}
            >
              {!listing?.semester && <option value="">Select a semester</option>}
              {options.map((s) => (
                <option key={s.code} value={s.code}>
                  {`${s.name} (${s.code})`}
                </option>
              ))}
            </select>
          </div>
          <div className="courses-toolbar__search">
            <Input
              data-testid="course-search-input"
              aria-label="Search courses"
              prefix={<SearchOutlined className="courses-toolbar__search-icon" />}
              placeholder="Search by course code or name"
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              onPressEnter={handleSearch}
            />
            <Button
              type="primary"
              htmlType="button"
              data-testid="course-search-button"
              onClick={handleSearch}
            >
              Search
            </Button>
          </div>
        </div>
        {renderBody()}
      </Card>
    </div>
  );
}
