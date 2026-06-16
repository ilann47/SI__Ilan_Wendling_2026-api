import { useState, type ReactNode } from 'react';
import dayjs, { type Dayjs } from 'dayjs';
import { useQuery } from '@tanstack/react-query';
import {
  Box,
  Card,
  CardContent,
  Chip,
  Stack,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tabs,
  Typography,
} from '@mui/material';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import PaidIcon from '@mui/icons-material/Paid';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import StorefrontIcon from '@mui/icons-material/Storefront';
import DesignServicesIcon from '@mui/icons-material/DesignServices';
import { api } from '../api/client';
import { PageHeader } from '../components/common/PageHeader';
import { KpiCard } from '../components/common/KpiCard';
import { StatusChip } from '../components/common/StatusChip';
import { formatCurrency, formatDate, formatNumber } from '../utils/format';
import type {
  ContasAVencerResponse,
  EstoqueMinimoResponse,
  FaturamentoDiaResponse,
  MensalistasAtivosResponse,
  Titulo,
} from '../types';

function TabPanel({ value, index, children }: { value: number; index: number; children: ReactNode }) {
  return value === index ? <Box sx={{ pt: 3 }}>{children}</Box> : null;
}

const kpiGrid = {
  display: 'grid',
  gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
  gap: 2,
} as const;

function FaturamentoTab() {
  const [inicio, setInicio] = useState<Dayjs>(dayjs());
  const [fim, setFim] = useState<Dayjs>(dayjs());
  const { data } = useQuery({
    queryKey: ['rel', 'faturamento', inicio.format('YYYY-MM-DD'), fim.format('YYYY-MM-DD')],
    queryFn: () =>
      api
        .get<FaturamentoDiaResponse>('/api/relatorios/faturamento', {
          params: { inicio: inicio.format('YYYY-MM-DD'), fim: fim.format('YYYY-MM-DD') },
        })
        .then((r) => r.data),
  });

  return (
    <Stack spacing={3}>
      <Stack direction="row" spacing={2}>
        <DatePicker label="Início" value={inicio} onChange={(d) => d && setInicio(d)} slotProps={{ textField: { size: 'small' } }} />
        <DatePicker label="Fim" value={fim} onChange={(d) => d && setFim(d)} slotProps={{ textField: { size: 'small' } }} />
      </Stack>
      <Box sx={kpiGrid}>
        <KpiCard title="Estacionamento" value={formatCurrency(data?.estacionamento)} icon={<PaidIcon />} />
        <KpiCard title="Vendas (conveniência)" value={formatCurrency(data?.vendas)} icon={<StorefrontIcon />} color="secondary.main" />
        <KpiCard title="Serviços" value={formatCurrency(data?.servicos)} icon={<DesignServicesIcon />} color="info.main" />
        <KpiCard title="Total faturado" value={formatCurrency(data?.totalFaturado)} subtitle={`Recebido: ${formatCurrency(data?.recebimentos)}`} icon={<TrendingUpIcon />} color="success.main" />
      </Box>
    </Stack>
  );
}

function TitulosTable({ titulos }: { titulos: Titulo[] }) {
  if (titulos.length === 0) {
    return (
      <Typography variant="body2" color="text.secondary" sx={{ py: 2 }}>
        Nenhum título no período.
      </Typography>
    );
  }
  return (
    <TableContainer>
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell>Origem</TableCell>
            <TableCell>Contraparte</TableCell>
            <TableCell>Vencimento</TableCell>
            <TableCell align="right">Saldo</TableCell>
            <TableCell>Situação</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {titulos.map((t) => (
            <TableRow key={`${t.origem}-${t.id}`}>
              <TableCell>{t.origem}</TableCell>
              <TableCell>{t.contraparte}</TableCell>
              <TableCell>
                {formatDate(t.vencimento)} {t.vencido && <Chip size="small" color="error" label="vencido" sx={{ ml: 1 }} />}
              </TableCell>
              <TableCell align="right">{formatCurrency(t.saldo)}</TableCell>
              <TableCell>
                <StatusChip status={t.situacao} />
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
}

function ContasAVencerTab() {
  const [inicio, setInicio] = useState<Dayjs>(dayjs());
  const [fim, setFim] = useState<Dayjs>(dayjs().add(30, 'day'));
  const { data } = useQuery({
    queryKey: ['rel', 'contas-a-vencer', inicio.format('YYYY-MM-DD'), fim.format('YYYY-MM-DD')],
    queryFn: () =>
      api
        .get<ContasAVencerResponse>('/api/relatorios/contas-a-vencer', {
          params: { inicio: inicio.format('YYYY-MM-DD'), fim: fim.format('YYYY-MM-DD') },
        })
        .then((r) => r.data),
  });

  return (
    <Stack spacing={3}>
      <Stack direction="row" spacing={2}>
        <DatePicker label="Início" value={inicio} onChange={(d) => d && setInicio(d)} slotProps={{ textField: { size: 'small' } }} />
        <DatePicker label="Fim" value={fim} onChange={(d) => d && setFim(d)} slotProps={{ textField: { size: 'small' } }} />
      </Stack>
      <Box sx={kpiGrid}>
        <KpiCard title="A pagar" value={formatCurrency(data?.totalAPagar)} subtitle={`Vencido: ${formatCurrency(data?.vencidoAPagar)}`} icon={<PaidIcon />} color="error.main" />
        <KpiCard title="A receber" value={formatCurrency(data?.totalAReceber)} subtitle={`Vencido: ${formatCurrency(data?.vencidoAReceber)}`} icon={<TrendingUpIcon />} color="success.main" />
      </Box>
      <Card>
        <CardContent>
          <Typography variant="subtitle1" sx={{ mb: 1 }}>
            Contas a pagar
          </Typography>
          <TitulosTable titulos={data?.aPagar ?? []} />
        </CardContent>
      </Card>
      <Card>
        <CardContent>
          <Typography variant="subtitle1" sx={{ mb: 1 }}>
            Contas a receber
          </Typography>
          <TitulosTable titulos={data?.aReceber ?? []} />
        </CardContent>
      </Card>
    </Stack>
  );
}

function EstoqueTab() {
  const { data } = useQuery({
    queryKey: ['rel', 'estoque'],
    queryFn: () => api.get<EstoqueMinimoResponse>('/api/relatorios/estoque-minimo').then((r) => r.data),
  });
  const itens = data?.itens ?? [];
  return (
    <Card>
      <CardContent>
        <Typography variant="subtitle1" sx={{ mb: 1 }}>
          {data?.total ?? 0} produto(s) no/abaixo do mínimo
        </Typography>
        {itens.length === 0 ? (
          <Typography variant="body2" color="text.secondary" sx={{ py: 2 }}>
            Estoque saudável.
          </Typography>
        ) : (
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Produto</TableCell>
                  <TableCell>Marca</TableCell>
                  <TableCell>Categoria</TableCell>
                  <TableCell align="right">Estoque</TableCell>
                  <TableCell align="right">Mínimo</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {itens.map((it) => (
                  <TableRow key={it.produtoId}>
                    <TableCell>{it.produto}</TableCell>
                    <TableCell>{it.marca}</TableCell>
                    <TableCell>{it.categoria || '—'}</TableCell>
                    <TableCell align="right">{formatNumber(it.quantidade, 0)}</TableCell>
                    <TableCell align="right">{formatNumber(it.quantidadeMinima, 0)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </CardContent>
    </Card>
  );
}

function MensalistasTab() {
  const { data } = useQuery({
    queryKey: ['rel', 'mensalistas'],
    queryFn: () => api.get<MensalistasAtivosResponse>('/api/relatorios/mensalistas-ativos').then((r) => r.data),
  });
  const itens = data?.itens ?? [];
  return (
    <Stack spacing={3}>
      <Box sx={kpiGrid}>
        <KpiCard title="Mensalistas ativos" value={data?.total ?? '—'} icon={<TrendingUpIcon />} />
        <KpiCard title="Receita recorrente" value={formatCurrency(data?.receitaMensalRecorrente)} icon={<PaidIcon />} color="success.main" />
      </Box>
      <Card>
        <CardContent>
          {itens.length === 0 ? (
            <Typography variant="body2" color="text.secondary" sx={{ py: 2 }}>
              Nenhum mensalista ativo.
            </Typography>
          ) : (
            <TableContainer>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Cliente</TableCell>
                    <TableCell>Veículo</TableCell>
                    <TableCell>Modelo</TableCell>
                    <TableCell align="right">Mensalidade</TableCell>
                    <TableCell>Início</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {itens.map((it) => (
                    <TableRow key={it.mensalistaId}>
                      <TableCell>{it.cliente}</TableCell>
                      <TableCell>{it.placa}</TableCell>
                      <TableCell>{it.modelo || '—'}</TableCell>
                      <TableCell align="right">{formatCurrency(it.valorMensal)}</TableCell>
                      <TableCell>{formatDate(it.dataInicio)}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </CardContent>
      </Card>
    </Stack>
  );
}

export function RelatoriosPage() {
  const [tab, setTab] = useState(0);
  return (
    <Box>
      <PageHeader title="Relatórios" subtitle="Consultas gerenciais consolidadas." />
      <Tabs value={tab} onChange={(_, v) => setTab(v)} variant="scrollable" scrollButtons="auto">
        <Tab label="Faturamento" />
        <Tab label="Contas a vencer" />
        <Tab label="Estoque mínimo" />
        <Tab label="Mensalistas ativos" />
      </Tabs>
      <TabPanel value={tab} index={0}>
        <FaturamentoTab />
      </TabPanel>
      <TabPanel value={tab} index={1}>
        <ContasAVencerTab />
      </TabPanel>
      <TabPanel value={tab} index={2}>
        <EstoqueTab />
      </TabPanel>
      <TabPanel value={tab} index={3}>
        <MensalistasTab />
      </TabPanel>
    </Box>
  );
}
