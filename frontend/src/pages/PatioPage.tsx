import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Stack,
  Typography,
} from '@mui/material';
import LoginIcon from '@mui/icons-material/Login';
import LogoutIcon from '@mui/icons-material/Logout';
import { api, describeError } from '../api/client';
import { PageHeader } from '../components/common/PageHeader';
import { useSnackbar } from '../components/SnackbarProvider';
import { ReferenceSelect } from '../components/form/ReferenceSelect';
import { formatCurrency, formatDateTime, minutesToHuman } from '../utils/format';
import type { MovimentacaoResponse, PatioAtualResponse } from '../types';

export function PatioPage() {
  const queryClient = useQueryClient();
  const { notify } = useSnackbar();
  const [veiculoId, setVeiculoId] = useState<number | null>(null);

  const patioQ = useQuery({
    queryKey: ['rel', 'patio'],
    queryFn: () => api.get<PatioAtualResponse>('/api/relatorios/patio').then((r) => r.data),
    refetchInterval: 15_000,
  });

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['rel', 'patio'] });

  const entrada = useMutation({
    mutationFn: () => api.post<MovimentacaoResponse>('/api/movimentacoes/entrada', { veiculoId }),
    onSuccess: () => {
      notify('Entrada registrada.', 'success');
      setVeiculoId(null);
      refresh();
    },
    onError: (e) => notify(describeError(e), 'error'),
  });

  const saida = useMutation({
    mutationFn: (id: number) =>
      api.post<MovimentacaoResponse>(`/api/movimentacoes/${id}/saida`).then((r) => r.data),
    onSuccess: (mov) => {
      notify(`Saída registrada. Cobrança: ${formatCurrency(mov.valorCobrado)}`, 'success');
      refresh();
    },
    onError: (e) => notify(describeError(e), 'error'),
  });

  const itens = patioQ.data?.itens ?? [];
  const resumo = patioQ.data?.resumo;

  return (
    <Box>
      <PageHeader
        title="Operação do pátio"
        subtitle="Registre entradas e saídas de veículos."
        action={
          resumo && (
            <Stack direction="row" spacing={1}>
              <Chip color="primary" label={`${resumo.totalVeiculos} no pátio`} />
              <Chip variant="outlined" label={`${resumo.avulsos} avulsos`} />
              <Chip variant="outlined" label={`${resumo.mensalistas} mensalistas`} />
            </Stack>
          )
        }
      />

      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="subtitle1" sx={{ mb: 2 }}>
            Registrar entrada
          </Typography>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ sm: 'center' }}>
            <Box sx={{ flexGrow: 1, maxWidth: 480 }}>
              <ReferenceSelect
                label="Veículo (placa)"
                value={veiculoId}
                onChange={setVeiculoId}
                reference={{ basePath: '/api/veiculos', labelField: 'placa', secondaryField: 'modelo' }}
              />
            </Box>
            <Button
              variant="contained"
              startIcon={<LoginIcon />}
              disabled={!veiculoId || entrada.isPending}
              onClick={() => entrada.mutate()}
            >
              Registrar entrada
            </Button>
          </Stack>
        </CardContent>
      </Card>

      <Typography variant="subtitle1" sx={{ mb: 1.5 }}>
        Veículos no pátio
      </Typography>

      {patioQ.isLoading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}>
          <CircularProgress />
        </Box>
      ) : itens.length === 0 ? (
        <Card>
          <CardContent>
            <Typography variant="body2" color="text.secondary" sx={{ py: 4, textAlign: 'center' }}>
              Nenhum veículo no pátio no momento.
            </Typography>
          </CardContent>
        </Card>
      ) : (
        <Box
          sx={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))',
            gap: 2,
          }}
        >
          {itens.map((it) => (
            <Card key={it.movimentacaoId}>
              <CardContent>
                <Stack direction="row" justifyContent="space-between" alignItems="center">
                  <Typography variant="h6">{it.placa}</Typography>
                  <Chip
                    size="small"
                    color={it.tipo === 'MENSALISTA' ? 'secondary' : 'default'}
                    label={it.tipo === 'MENSALISTA' ? 'Mensalista' : 'Avulso'}
                  />
                </Stack>
                <Typography variant="body2" color="text.secondary">
                  {it.modelo || 'Modelo não informado'}
                </Typography>
                <Stack spacing={0.5} sx={{ mt: 1.5, mb: 2 }}>
                  <Typography variant="caption" color="text.secondary">
                    Entrada: {formatDateTime(it.dataEntrada)}
                  </Typography>
                  <Typography variant="body2">
                    Permanência: <strong>{minutesToHuman(it.minutosPermanencia)}</strong>
                  </Typography>
                </Stack>
                <Button
                  fullWidth
                  variant="outlined"
                  color="warning"
                  startIcon={<LogoutIcon />}
                  disabled={saida.isPending}
                  onClick={() => saida.mutate(it.movimentacaoId)}
                >
                  Registrar saída
                </Button>
              </CardContent>
            </Card>
          ))}
        </Box>
      )}
    </Box>
  );
}
