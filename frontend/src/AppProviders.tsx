import { type ReactNode } from 'react';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';
import 'dayjs/locale/pt-br';
import { ColorModeProvider } from './context/ColorModeContext';
import { SnackbarProvider } from './components/SnackbarProvider';
import { AuthProvider } from './auth/AuthContext';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, refetchOnWindowFocus: false, staleTime: 30_000 },
  },
});

export function AppProviders({ children }: { children: ReactNode }) {
  return (
    <BrowserRouter>
      <QueryClientProvider client={queryClient}>
        <ColorModeProvider>
          <LocalizationProvider dateAdapter={AdapterDayjs} adapterLocale="pt-br">
            <SnackbarProvider>
              <AuthProvider>{children}</AuthProvider>
            </SnackbarProvider>
          </LocalizationProvider>
        </ColorModeProvider>
      </QueryClientProvider>
    </BrowserRouter>
  );
}
