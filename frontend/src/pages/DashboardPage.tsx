import { Link as RouterLink } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import dayjs from 'dayjs';
import {
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Divider,
  List,
  ListItem,
  ListItemText,
  Stack,
  Typography,
} from '@mui/material';
import LocalParkingIcon from '@mui/icons-material/LocalParking';
import PaidIcon from '@mui/icons-material/Paid';
import CardMembershipIcon from '@mui/icons-material/CardMembership';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import { api } from '../api/client';
import { PageHeader } from '../components/common/PageHeader';
import { KpiCard } from '../components/common/KpiCard';
import { formatCurrency, formatNumber, minutesToHuman } from '../utils/format';
import type {
  EstoqueMinimoResponse,
  FaturamentoDiaResponse,
  MensalistasAtivosResponse,
  PatioAtualResponse,
} from '../types';

export function DashboardPage() {
  const hoje = dayjs().format('YYYY-MM-DD');

  const patioQ = useQuery({
    queryKey: ['rel', 'patio'],
    queryFn: () => api.get<PatioAtualResponse>('/api/relatorios/patio').then((r) => r.data),
    refetchInterval: 20_000,
  });
  const fatQ = useQuery({
    queryKey: ['rel', 'faturamento', hoje],
    queryFn: () =>
      api
        .get<FaturamentoDiaResponse>('/api/relatorios/faturamento', { params: { inicio: hoje } })
        .then((r) => r.data),
  });
  const estoqueQ = useQuery({
    queryKey: ['rel', 'estoque'],
    queryFn: () => api.get<EstoqueMinimoResponse>('/api/relatorios/estoque-minimo').then((r) => r.data),
  });
  const mensQ = useQuery({
    queryKey: ['rel', 'mensalistas'],
    queryFn: () =>
      api.get<MensalistasAtivosResponse>('/api/relatorios/mensalistas-ativos').then((r) => r.data),
  });

  const patio = patioQ.data;
  const itensPatio = patio?.itens ?? [];
  const itensEstoque = estoqueQ.data?.itens ?? [];

  return (
    <Box>
      <PageHeader title="Dashboard" subtitle="Visão geral da operação do estacionamento." />

      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
          gap: 2,
          mb: 3,
        }}
      >
        <KpiCard
          title="No pátio agora"
          value={patio?.resumo.totalVeiculos ?? '—'}
          subtitle={`${patio?.resumo.avulsos ?? 0} avulsos · ${patio?.resumo.mensalistas ?? 0} mensalistas`}
          icon={<LocalParkingIcon />}
        />
        <KpiCard
          title="Faturamento hoje"
          value={formatCurrency(fatQ.data?.totalFaturado)}
          subtitle={`Recebido: ${formatCurrency(fatQ.data?.recebimentos)}`}
          icon={<PaidIcon />}
          color="success.main"
        />
        <KpiCard
          title="Receita recorrente"
          value={formatCurrency(mensQ.data?.receitaMensalRecorrente)}
          subtitle={`${mensQ.data?.total ?? 0} mensalistas ativos`}
          icon={<CardMembershipIcon />}
          color="secondary.main"
        />
        <KpiCard
          title="Estoque baixo"
          value={estoqueQ.data?.total ?? '—'}
          subtitle="produtos no/abaixo do mínimo"
          icon={<WarningAmberIcon />}
          color="warning.main"
        />
      </Box>

      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' }, gap: 2 }}>
        <Card>
          <CardContent>
            <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
              <Typography variant="subtitle1">Pátio atual</Typography>
              <Button size="small" component={RouterLink} to="/app/patio">
                Abrir pátio
              </Button>
            </Stack>
            <Divider />
            {itensPatio.length === 0 ? (
              <Typography variant="body2" color="text.secondary" sx={{ py: 3, textAlign: 'center' }}>
                Nenhum veículo no pátio.
              </Typography>
            ) : (
              <List dense>
                {itensPatio.slice(0, 6).map((it) => (
                  <ListItem
                    key={it.movimentacaoId}
                    secondaryAction={<Chip size="small" label={minutesToHuman(it.minutosPermanencia)} />}
                  >
                    <ListItemText
                      primary={`${it.placa}${it.modelo ? ` · ${it.modelo}` : ''}`}
                      secondary={it.tipo === 'MENSALISTA' ? 'Mensalista' : 'Avulso'}
                    />
                  </ListItem>
                ))}
              </List>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardContent>
            <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
              <Typography variant="subtitle1">Estoque baixo</Typography>
              <Button size="small" component={RouterLink} to="/app/relatorios">
                Relatórios
              </Button>
            </Stack>
            <Divider />
            {itensEstoque.length === 0 ? (
              <Typography variant="body2" color="text.secondary" sx={{ py: 3, textAlign: 'center' }}>
                Nenhum produto abaixo do mínimo.
              </Typography>
            ) : (
              <List dense>
                {itensEstoque.slice(0, 6).map((it) => (
                  <ListItem
                    key={it.produtoId}
                    secondaryAction={
                      <Chip size="small" color="warning" label={formatNumber(it.quantidade, 0)} />
                    }
                  >
                    <ListItemText
                      primary={it.produto}
                      secondary={`${it.marca}${it.categoria ? ` · ${it.categoria}` : ''} — mín. ${formatNumber(it.quantidadeMinima, 0)}`}
                    />
                  </ListItem>
                ))}
              </List>
            )}
          </CardContent>
        </Card>
      </Box>
    </Box>
  );
}
