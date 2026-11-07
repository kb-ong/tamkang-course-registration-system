// Shared Vitest setup (owned by the tester). See docs/specs/FR1-login.md §3.5.
import "@testing-library/jest-dom/vitest";
import { cleanup, configure } from "@testing-library/react";
import { afterEach, vi } from "vitest";

// antd renders asynchronously (form validation, transitions); give findBy*/waitFor some headroom.
configure({ asyncUtilTimeout: 3000 });

// Typing into antd inputs with userEvent in jsdom takes 3-7 s per login-flow test, close to
// Vitest's 5 s default. vite.config.ts's test block is fixed by the spec, so raise it here.
vi.setConfig({ testTimeout: 20000 });

afterEach(() => {
  cleanup();
});

// antd Layout.Sider breakpoints and Grid use matchMedia, which jsdom lacks.
if (!window.matchMedia) {
  Object.defineProperty(window, "matchMedia", {
    writable: true,
    configurable: true,
    value: vi.fn().mockImplementation((query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
}

// rc-resize-observer (antd) needs ResizeObserver.
if (!("ResizeObserver" in window)) {
  class ResizeObserverStub {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
  Object.defineProperty(window, "ResizeObserver", {
    writable: true,
    configurable: true,
    value: ResizeObserverStub,
  });
  (globalThis as unknown as { ResizeObserver: unknown }).ResizeObserver = ResizeObserverStub;
}

// jsdom does not implement getComputedStyle(elt, pseudoElt) and logs "Not implemented";
// antd calls it with a pseudo element when measuring scrollbars. Ignore the pseudo element.
const originalGetComputedStyle = window.getComputedStyle.bind(window);
window.getComputedStyle = ((elt: Element, _pseudoElt?: string | null) =>
  originalGetComputedStyle(elt)) as typeof window.getComputedStyle;
