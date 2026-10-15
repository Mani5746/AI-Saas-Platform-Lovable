import { Component, type ErrorInfo, type ReactNode } from "react";

interface ErrorBoundaryProps {
  children: ReactNode;
}

interface ErrorBoundaryState {
  error: Error | null;
}

/**
 * Catches render errors anywhere in the app and shows them on the page,
 * instead of React unmounting everything and leaving a blank screen.
 */
export default class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { error: null };

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    console.error("App crashed:", error, info.componentStack);
  }

  render(): ReactNode {
    const { error } = this.state;
    if (!error) {
      return this.props.children;
    }
    return (
      <main className="min-h-screen flex items-center justify-center bg-base-200 p-6">
        <section role="alert" className="card w-full max-w-2xl bg-base-100 shadow-xl">
          <div className="card-body gap-4">
            <h1 className="card-title text-error">Something went wrong</h1>
            <p>The app hit an error while rendering. Details:</p>
            <pre className="bg-base-200 rounded-lg p-4 text-sm overflow-auto whitespace-pre-wrap">
              {error.message}
            </pre>
            <div className="card-actions justify-end">
              <button className="btn btn-primary" onClick={() => window.location.reload()}>
                Reload
              </button>
            </div>
          </div>
        </section>
      </main>
    );
  }
}
