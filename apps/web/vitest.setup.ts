import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

// React Testing Library keeps mounted trees between tests unless they are torn down.
afterEach(() => cleanup());
