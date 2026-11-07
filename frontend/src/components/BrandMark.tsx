import { ReadOutlined } from '@ant-design/icons';

/** Generic circular brand icon (no real university marks). */
export function BrandMark({ size = 40 }: { size?: number }) {
  return (
    <span
      className="brand-mark"
      style={{ width: size, height: size, fontSize: Math.round(size * 0.5) }}
      aria-hidden="true"
    >
      <ReadOutlined />
    </span>
  );
}
