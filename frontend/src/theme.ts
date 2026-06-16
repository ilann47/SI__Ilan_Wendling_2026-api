import { createTheme, type ThemeOptions } from '@mui/material/styles';
import { ptBR } from '@mui/material/locale';

export type AppColorMode = 'light' | 'dark';

/**
 * Tema MUI da aplicacao. Visual moderno: cantos arredondados, tipografia Inter,
 * AppBar/Drawer claros, cards com borda sutil. Suporta modo claro/escuro.
 */
export function createAppTheme(mode: AppColorMode) {
  const isLight = mode === 'light';
  const options: ThemeOptions = {
    palette: {
      mode,
      primary: { main: '#1565c0' },
      secondary: { main: '#00897b' },
      success: { main: '#2e7d32' },
      warning: { main: '#ed6c02' },
      error: { main: '#d32f2f' },
      background: isLight
        ? { default: '#f4f6f8', paper: '#ffffff' }
        : { default: '#0f141a', paper: '#161c24' },
    },
    shape: { borderRadius: 10 },
    typography: {
      fontFamily: '"Inter", Roboto, system-ui, -apple-system, sans-serif',
      h4: { fontWeight: 700 },
      h5: { fontWeight: 700 },
      h6: { fontWeight: 600 },
      subtitle1: { fontWeight: 600 },
      button: { textTransform: 'none', fontWeight: 600 },
    },
    components: {
      MuiButton: { defaultProps: { disableElevation: true } },
      MuiPaper: { styleOverrides: { root: { backgroundImage: 'none' } } },
      MuiCard: {
        defaultProps: { elevation: 0 },
        styleOverrides: {
          root: {
            border: '1px solid',
            borderColor: isLight ? 'rgba(15,23,42,0.08)' : 'rgba(255,255,255,0.08)',
          },
        },
      },
      MuiAppBar: {
        defaultProps: { elevation: 0, color: 'default' },
        styleOverrides: {
          root: {
            backgroundColor: isLight ? '#ffffff' : '#161c24',
            borderBottom: '1px solid',
            borderColor: isLight ? 'rgba(15,23,42,0.08)' : 'rgba(255,255,255,0.08)',
          },
        },
      },
      MuiTextField: { defaultProps: { size: 'small' } },
    },
  };
  return createTheme(options, ptBR);
}
