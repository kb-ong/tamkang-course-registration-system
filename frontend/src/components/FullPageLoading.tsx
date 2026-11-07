import { Spin } from 'antd';

/** Centred spinner used while the session is being checked. */
export function FullPageLoading() {
  return (
    <div data-testid="auth-loading" className="full-page-loading">
      <Spin size="large" tip="Loading...">
        <div className="full-page-loading__spacer" />
      </Spin>
    </div>
  );
}
