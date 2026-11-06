import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Toaster } from 'sonner'
import { App } from './App'
import { applyTheme, getPref, type Theme } from './lib/prefs'
import './styles.css'

applyTheme(getPref<Theme>('theme', 'system'))

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: (count, error) => count < 2 && !(error as { code?: string })?.code?.startsWith?.('PGRST'),
    },
  },
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <App />
      <Toaster
        position="bottom-center"
        offset={96}
        toastOptions={{
          unstyled: true,
          classNames: {
            toast: 'flex w-[min(92vw,420px)] items-center gap-3 rounded-full bg-ink px-5 py-3.5 text-[15px] font-medium text-on-ink shadow-[var(--shadow-float)]',
            actionButton: 'ml-auto rounded-full px-2 font-semibold text-accent-soft underline underline-offset-4',
          },
        }}
      />
    </QueryClientProvider>
  </StrictMode>,
)
